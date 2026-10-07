package test

import ext.JACKSON
import icu.samnyan.aqua.net.AccountDeletionService
import icu.samnyan.aqua.net.AdminApi
import icu.samnyan.aqua.net.AdminPasswordResetRequest
import icu.samnyan.aqua.net.components.JWT
import icu.samnyan.aqua.net.db.*
import icu.samnyan.aqua.net.utils.ApiException
import icu.samnyan.aqua.net.utils.GlobalExceptionHandler
import icu.samnyan.aqua.sega.allnet.AllNetProps
import icu.samnyan.aqua.sega.allnet.KeychipSessionRepo
import icu.samnyan.aqua.sega.allnet.UserKeychipRepo
import icu.samnyan.aqua.sega.chusan.model.Chu3Repos
import icu.samnyan.aqua.sega.general.GameMusicPopularity
import icu.samnyan.aqua.sega.general.dao.CardRepository
import icu.samnyan.aqua.sega.general.service.CardService
import icu.samnyan.aqua.sega.maimai2.model.Mai2Repos
import icu.samnyan.aqua.sega.ongeki.OngekiRepos
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDate
import java.time.ZoneId

private class AdminFixture {
    val jwt = mock(JWT::class.java)
    val users = mock(AquaNetUserRepo::class.java)
    val sessions = mock(SessionTokenRepo::class.java)
    val resets = mock(ResetPasswordRepo::class.java)
    val hasher = BCryptPasswordEncoder(4)
    val actor = AquaNetUser(auId = 1, username = "admin", isAdmin = true)
    val target = AquaNetUser(auId = 2, username = "dongyin", pwHash = "previous-hash")
    val userServices = AquaUserServices(
        users, mock(CardRepository::class.java), hasher, mock(UserKeychipRepo::class.java),
        mock(AllNetProps::class.java), jwt, mock(EntityManager::class.java),
        mock(GameMusicPopularity::class.java), mock(CardService::class.java), sessions,
    )
    val api = AdminApi(
        jwt, users, mock(CardRepository::class.java), mock(UserKeychipRepo::class.java),
        mock(KeychipSessionRepo::class.java), mock(Mai2Repos::class.java), mock(Chu3Repos::class.java),
        mock(OngekiRepos::class.java), mock(AccountDeletionService::class.java), userServices, resets,
    )

    init {
        `when`(jwt.auth("admin-token")).thenReturn(actor)
        `when`(users.findByAuId(target.auId)).thenReturn(target)
    }

    fun mvc() = MockMvcBuilders.standaloneSetup(api)
        .setControllerAdvice(GlobalExceptionHandler())
        .setMessageConverters(JacksonJsonHttpMessageConverter(JACKSON))
        .build()
}

private fun registeredOn(date: String) = LocalDate.parse(date)
    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

class AdminAccountManagementTest : StringSpec({
    "non-admins cannot reset another account or search accounts" {
        val f = AdminFixture()
        f.actor.isAdmin = false
        shouldThrow<ApiException> {
            f.api.resetUserPassword("admin-token", 2, AdminPasswordResetRequest("new-password"))
        }.code shouldBe 403
        shouldThrow<ApiException> { f.api.users("admin-token", "dong") }.code shouldBe 403
        f.target.pwHash shouldBe "previous-hash"
        verifyNoInteractions(f.users, f.sessions, f.resets)
    }

    "a missing account or invalid password leaves credentials and sessions unchanged" {
        val f = AdminFixture()
        shouldThrow<ApiException> {
            f.api.resetUserPassword("admin-token", 999, AdminPasswordResetRequest("new-password"))
        }.code shouldBe 404
        shouldThrow<ApiException> {
            f.api.resetUserPassword("admin-token", 2, AdminPasswordResetRequest("short"))
        }.code shouldBe 400
        f.target.pwHash shouldBe "previous-hash"
        verify(f.users, never()).save(f.target)
        verifyNoInteractions(f.sessions, f.resets)
    }

    "reset stores a login-compatible hash and revokes all target sessions and reset links" {
        val f = AdminFixture()
        val sessions = listOf(SessionToken(aquaNetUser = f.target), SessionToken(aquaNetUser = f.target))
        val links = listOf(ResetPassword(aquaNetUser = f.target))
        `when`(f.sessions.findByAquaNetUserAuId(2)).thenReturn(sessions)
        `when`(f.resets.findByAquaNetUserAuId(2)).thenReturn(links)

        f.api.resetUserPassword("admin-token", 2, AdminPasswordResetRequest("new-password")) shouldBe mapOf("success" to true)

        f.hasher.matches("new-password", f.target.pwHash) shouldBe true
        f.hasher.matches("old-password", f.target.pwHash) shouldBe false
        verify(f.users).save(f.target)
        verify(f.sessions).deleteAll(sessions)
        verify(f.resets).deleteAll(links)
        verify(f.sessions, never()).findByAquaNetUserAuId(1)
        verify(f.resets, never()).findByAquaNetUserAuId(1)
    }

    "password reset requires a POST JSON body and returns no credentials" {
        val f = AdminFixture()
        val mvc = f.mvc()
        val response = mvc.perform(post("/api/v2/admin/user/password-reset")
            .param("token", "admin-token").param("auId", "2")
            .contentType(MediaType.APPLICATION_JSON).content("""{"password":"new-password"}"""))
            .andExpect(status().isOk).andReturn().response.contentAsString
        JACKSON.readTree(response) shouldBe JACKSON.readTree("""{"success":true}""")
        f.hasher.matches("new-password", f.target.pwHash) shouldBe true
        mvc.perform(get("/api/v2/admin/user/password-reset").param("token", "admin-token").param("auId", "2"))
            .andExpect(status().isMethodNotAllowed)
        mvc.perform(post("/api/v2/admin/user/password-reset").param("token", "admin-token")
            .param("auId", "2").param("password", "query-password"))
            .andExpect(status().isBadRequest)
        f.hasher.matches("new-password", f.target.pwHash) shouldBe true
    }

    "username search matches substrings ignoring case and includes all registration dates by default" {
        val f = AdminFixture()
        val old = AquaNetUser(auId = 2, username = "DongYin", regTime = registeredOn("2019-01-01"))
        val recent = AquaNetUser(auId = 3, username = "other", regTime = registeredOn("2026-10-08"))
        `when`(f.users.findAll()).thenReturn(listOf(old, recent))
        f.api.users("admin-token", "  ONGyi  ").map { it["auId"] } shouldBe listOf(2L)
        f.api.users("admin-token").map { it["auId"] } shouldBe listOf(2L, 3L)
        f.api.users("admin-token", "", " ", "").map { it["auId"] } shouldBe listOf(2L, 3L)
        f.mvc().perform(post("/api/v2/admin/users").param("token", "admin-token").param("query", "yin"))
            .andExpect(status().isOk).andReturn().response.contentAsString.let {
                JACKSON.readTree(it).size() shouldBe 1
            }
    }

    "registration filters support open ranges and include the entire end date" {
        val f = AdminFixture()
        `when`(f.users.findAll()).thenReturn(listOf(
            AquaNetUser(auId = 1, regTime = registeredOn("2026-10-06")),
            AquaNetUser(auId = 2, regTime = registeredOn("2026-10-07") + 86_399_000),
            AquaNetUser(auId = 3, regTime = registeredOn("2026-10-08")),
        ))
        f.api.users("admin-token", regFrom = "2026-10-07", regTo = "2026-10-07")
            .map { it["auId"] } shouldBe listOf(2L)
        f.api.users("admin-token", regTo = "2026-10-07").map { it["auId"] } shouldBe listOf(1L, 2L)
        f.api.users("admin-token", regFrom = "2026-10-07").map { it["auId"] } shouldBe listOf(2L, 3L)
    }

    "invalid and reversed dates report a client error instead of searching an unintended range" {
        val f = AdminFixture()
        shouldThrow<ApiException> { f.api.users("admin-token", regFrom = "bad-date") }.code shouldBe 400
        shouldThrow<ApiException> {
            f.api.users("admin-token", regFrom = "2026-10-08", regTo = "2026-10-07")
        }.code shouldBe 400
        shouldThrow<ApiException> { f.api.cards("admin-token", regTo = "2026-02-30") }.code shouldBe 400
        verify(f.users, never()).findAll()
    }
})
