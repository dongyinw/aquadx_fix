package test

import ext.JACKSON
import ext.returns
import icu.samnyan.aqua.net.components.JWT
import icu.samnyan.aqua.net.db.AquaNetUser
import icu.samnyan.aqua.net.db.AquaUserServices
import icu.samnyan.aqua.net.games.mai2.Mai2Import
import icu.samnyan.aqua.net.games.mai2.Maimai2DataExport
import icu.samnyan.aqua.net.utils.ApiException
import icu.samnyan.aqua.net.utils.AquaNetProps
import icu.samnyan.aqua.sega.general.model.Card
import icu.samnyan.aqua.sega.general.service.CardService
import icu.samnyan.aqua.sega.maimai2.model.Mai2Repos
import icu.samnyan.aqua.sega.maimai2.model.Mai2UserLinked
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserDetail
import icu.samnyan.aqua.sega.maimai2.model.userdata.Mai2UserGeneralData
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito.*
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import kotlin.reflect.full.declaredMembers

private class SocialImportFixture : AutoCloseable {
    // Real foreign keys make a profile deletion cascade to the social table.
    val db: Connection = DriverManager.getConnection("jdbc:sqlite::memory:")
    val repos = mock(Mai2Repos::class.java, RETURNS_DEEP_STUBS)
    val profiles = repos.userData
    val general = repos.userGeneralData
    val music = repos.userMusicDetail
    val services = mock(AquaUserServices::class.java)
    val jwt = mock(JWT::class.java)
    val account = AquaNetUser(auId = 1)
    val card = Card(extId = 100, luid = "test-account-card", aquaUser = account, isGhost = true)
    val current = Mai2UserDetail(card = card, userName = "Before", playerRating = 100).apply { id = 649 }
    val backupDir = Files.createTempDirectory("mai2-import-test")
    val controller = Mai2Import(repos)
    var failMusicInsert = false

    fun sql(query: String) { db.createStatement().use { it.execute(query) } }
    fun scalar(query: String): String = db.createStatement().use { s ->
        s.executeQuery(query).use { rs -> check(rs.next()); rs.getString(1) }
    }

    init {
        sql("PRAGMA foreign_keys = ON")
        sql("CREATE TABLE profile (id INTEGER PRIMARY KEY, name TEXT)")
        sql("CREATE TABLE social (low_id INTEGER REFERENCES profile(id) ON DELETE CASCADE, high_id INTEGER REFERENCES profile(id) ON DELETE CASCADE, requested_by INTEGER REFERENCES profile(id) ON DELETE CASCADE, status TEXT)")
        sql("CREATE TABLE general (user_id INTEGER REFERENCES profile(id) ON DELETE CASCADE, property_key TEXT, property_value TEXT, UNIQUE(user_id, property_key))")
        sql("CREATE TABLE music (user_id INTEGER REFERENCES profile(id) ON DELETE CASCADE, music_id INTEGER)")
        sql("INSERT INTO profile VALUES (649, 'Before'), (650, 'Friend'), (651, 'Request')")
        sql("INSERT INTO social VALUES (649, 650, 649, 'ACCEPTED'), (649, 651, 651, 'PENDING')")
        sql("INSERT INTO general VALUES (649, 'favorite_rival', '650'), (650, 'favorite_rival', '649'), (649, 'favorite_music', 'old')")
        sql("INSERT INTO music VALUES (649, 1)")
        account.ghostCard = card
        `when`(services.jwt).thenReturn(jwt)
        `when`(jwt.auth("test-token")).thenReturn(account)
        `when`(repos.userData.findByCard(card)).thenReturn(current)
        doAnswer { sql("DELETE FROM profile WHERE id = 649"); null }.`when`(profiles).delete(current)
        doAnswer { invocation ->
            val profile = invocation.getArgument<Mai2UserDetail>(0)
            db.prepareStatement("UPDATE profile SET name = ? WHERE id = ?").use {
                it.setString(1, profile.userName); it.setLong(2, profile.id); it.executeUpdate()
            }
            profile
        }.`when`(profiles).save(any(Mai2UserDetail::class.java))

        Mai2Repos::class.declaredMembers.filter { it returns Mai2UserLinked::class }.forEach { field ->
            val repo = field.call(repos) as Mai2UserLinked<*>
            doReturn(null).`when`(repo).findSingleByUser(current)
        }
        doAnswer {
            db.createStatement().use { s ->
                s.executeQuery("SELECT property_key, property_value FROM general WHERE user_id = 649").use { rs ->
                    buildList { while (rs.next()) add(Mai2UserGeneralData().apply {
                        user = current; propertyKey = rs.getString(1); propertyValue = rs.getString(2)
                    }) }
                }
            }
        }.`when`(general).findByUser(current)
        doAnswer { invocation ->
            invocation.getArgument<Iterable<Mai2UserGeneralData>>(0).forEach { row ->
                db.prepareStatement("DELETE FROM general WHERE user_id = ? AND property_key = ?").use {
                    it.setLong(1, row.user.id); it.setString(2, row.propertyKey); it.executeUpdate()
                }
            }; null
        }.`when`(general).deleteAll(anyIterable())
        doAnswer { invocation ->
            val rows = invocation.getArgument<Iterable<Mai2UserGeneralData>>(0)
            rows.forEach { row ->
                db.prepareStatement("INSERT INTO general VALUES (?, ?, ?)").use {
                    it.setLong(1, row.user.id); it.setString(2, row.propertyKey)
                    it.setString(3, row.propertyValue); it.executeUpdate()
                }
            }; rows.toList()
        }.`when`(general).saveAll(anyIterable())
        doAnswer { sql("DELETE FROM music WHERE user_id = 649"); null }.`when`(music).deleteByUser(current)
        doAnswer {
            if (failMusicInsert) throw IllegalStateException("simulated import failure")
            emptyList<Any>()
        }.`when`(music).saveAll(anyIterable())
        `when`(repos.userGeneralData.findByUserAndPropertyKey(current, "favorite_music")).thenReturn(null)

        controller.us = services
        controller.cardService = mock(CardService::class.java)
        controller.netProps = AquaNetProps().apply { importBackupPath = backupDir.toString() }
        controller.transManager = object : PlatformTransactionManager {
            override fun getTransaction(definition: TransactionDefinition?): TransactionStatus {
                db.autoCommit = false
                return SimpleTransactionStatus()
            }
            override fun commit(status: TransactionStatus) { db.commit(); db.autoCommit = true }
            override fun rollback(status: TransactionStatus) { db.rollback(); db.autoCommit = true }
        }
    }

    fun importData() = controller.importUserData("test-token", JACKSON.writeValueAsString(Maimai2DataExport().apply {
        userData.userName = "After"
        userData.playerRating = 15742
        userGeneralDataList = listOf(Mai2UserGeneralData().apply {
            propertyKey = "favorite_rival"; propertyValue = "999999"
        }, Mai2UserGeneralData().apply {
            propertyKey = "test_property"; propertyValue = "imported"
        })
    }))

    override fun close() {
        db.close()
        Files.list(backupDir).use { files -> files.forEach { Files.delete(it) } }
        Files.delete(backupDir)
    }
}

class Mai2ImportSocialRetentionTest : StringSpec({
    "import updates the same profile and preserves friends, requests and both sides' rival IDs" {
        SocialImportFixture().use { f ->
            f.importData()
            f.current.id shouldBe 649
            f.current.card shouldBe f.card
            f.current.playerRating shouldBe 15742
            f.scalar("SELECT name FROM profile WHERE id = 649") shouldBe "After"
            f.scalar("SELECT COUNT(*) FROM social") shouldBe "2"
            f.scalar("SELECT property_value FROM general WHERE user_id = 649 AND property_key = 'favorite_rival'") shouldBe "650"
            f.scalar("SELECT property_value FROM general WHERE user_id = 650 AND property_key = 'favorite_rival'") shouldBe "649"
            f.scalar("SELECT COUNT(*) FROM general WHERE property_value = '999999'") shouldBe "0"
            f.scalar("SELECT property_value FROM general WHERE property_key = 'test_property'") shouldBe "imported"
            f.scalar("SELECT COUNT(*) FROM music WHERE user_id = 649") shouldBe "0"
            verify(f.repos.userData, never()).delete(f.current)
        }
    }

    "failed import rolls back profile and child replacement without losing social data" {
        SocialImportFixture().use { f ->
            f.failMusicInsert = true
            shouldThrow<ApiException> { f.importData() }.code shouldBe 500
            f.scalar("SELECT name FROM profile WHERE id = 649") shouldBe "Before"
            f.scalar("SELECT COUNT(*) FROM music WHERE user_id = 649") shouldBe "1"
            f.scalar("SELECT COUNT(*) FROM social") shouldBe "2"
            f.scalar("SELECT COUNT(*) FROM general WHERE property_key = 'test_property'") shouldBe "0"
            f.scalar("SELECT property_value FROM general WHERE user_id = 649 AND property_key = 'favorite_music'") shouldBe "old"
        }
    }
})
