package test

import icu.samnyan.aqua.net.*
import icu.samnyan.aqua.net.db.AquaNetUser
import icu.samnyan.aqua.net.db.AquaNetUserRepo
import icu.samnyan.aqua.net.games.GameApiController
import icu.samnyan.aqua.net.games.GenericUserDataRepo
import icu.samnyan.aqua.net.games.IUserData
import icu.samnyan.aqua.net.utils.ApiException
import icu.samnyan.aqua.sega.general.dao.PropertyEntryRepository
import icu.samnyan.aqua.sega.general.service.CardService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.springframework.beans.factory.ObjectProvider

@Suppress("UNCHECKED_CAST")
private class ReviewFixture {
    val store = HashMap<Long, GameNameRequest>()
    val requests = mock(GameNameRequestRepo::class.java)
    val users = mock(AquaNetUserRepo::class.java)
    val props = mock(PropertyEntryRepository::class.java)
    val features = FeatureSwitches(props)
    val player = AquaNetUser(auId = 5, username = "player")
    val data = mock(IUserData::class.java)
    var stored = "旧名字"
    val dataRepo = mock(GenericUserDataRepo::class.java) as GenericUserDataRepo<IUserData>
    val cards = mock(CardService::class.java)
    val ctrl = mock(GameApiController::class.java) as GameApiController<IUserData>
    val provider = mock(ObjectProvider::class.java) as ObjectProvider<GameApiController<*>>
    val service = NameReviewService(requests, users, features, provider)
    private var nextId = 1L

    init {
        `when`(data.userName).thenAnswer { stored }
        doAnswer { stored = it.getArgument(0); null }.`when`(data).userName = anyString()
        `when`(ctrl.name).thenReturn("mai2")
        `when`(ctrl.userDataRepo).thenReturn(dataRepo)
        `when`(ctrl.cardService).thenReturn(cards)
        `when`(dataRepo.findByCard(player.ghostCard)).thenReturn(data)
        `when`(provider.orderedStream()).thenAnswer { java.util.stream.Stream.of<GameApiController<*>>(ctrl) }
        `when`(users.findByAuId(5)).thenReturn(player)
        `when`(requests.save(any(GameNameRequest::class.java))).thenAnswer {
            (it.getArgument(0) as GameNameRequest).also { r -> if (r.id == 0L) r.id = nextId++; store[r.id] = r }
        }
        `when`(requests.findById(anyLong())).thenAnswer { java.util.Optional.ofNullable(store[it.getArgument<Long>(0)]) }
        `when`(requests.findByAuIdAndGameAndStatus(anyLong(), anyString(), anyString())).thenAnswer {
            store.values.filter { r -> r.auId == it.getArgument<Long>(0) && r.game == it.getArgument<String>(1) && r.status == it.getArgument<String>(2) }
        }
        `when`(requests.findByAuIdAndNotifiedFalseAndStatusNotOrderByReviewedAtAsc(anyLong(), anyString())).thenAnswer {
            store.values.filter { r -> r.auId == it.getArgument<Long>(0) && !r.notified && r.status != it.getArgument<String>(1) }.sortedBy { r -> r.reviewedAt }
        }
        `when`(requests.findByStatusAndSubmittedAtLessThanEqual(anyString(), anyLong())).thenAnswer {
            store.values.filter { r -> r.status == it.getArgument<String>(0) && r.submittedAt <= it.getArgument<Long>(1) }
        }
    }
}

private fun anyString() = any(String::class.java) ?: ""
private fun anyLong() = any(Long::class.java) ?: 0L

class NameReviewTest : StringSpec({
    "name review is off until an admin switches it on, and the old switches keep their defaults" {
        val f = ReviewFixture()
        f.features.enabled(Feature.NAME_REVIEW) shouldBe false
        f.features.enabled(Feature.RENAME) shouldBe true
        f.features.set(Feature.NAME_REVIEW, true)
        f.features.enabled(Feature.NAME_REVIEW) shouldBe true
        f.features.enabled(Feature.RENAME) shouldBe true
        f.features.set(Feature.NAME_REVIEW, false)
        f.features.enabled(Feature.NAME_REVIEW) shouldBe false
        f.features.set(Feature.RENAME, false)
        f.features.enabled(Feature.RENAME) shouldBe false
        f.features.enabled(Feature.NAME_REVIEW) shouldBe false
    }

    "a submitted name stays pending and is not applied until approved" {
        val f = ReviewFixture()
        val req = f.service.submit(f.player, "mai2", "旧名字", "新名字")
        req.status shouldBe NameReviewService.PENDING
        f.stored shouldBe "旧名字"
        f.service.approve(req.id, 1)
        req.status shouldBe NameReviewService.APPROVED
        req.autoApproved shouldBe false
        f.stored shouldBe "新名字"
        shouldThrow<ApiException> { f.service.approve(req.id, 1) }.code shouldBe 400
    }

    "submitting again replaces the open request, and the same name is refused" {
        val f = ReviewFixture()
        val first = f.service.submit(f.player, "mai2", "旧名字", "甲")
        val second = f.service.submit(f.player, "mai2", "旧名字", "乙")
        second.id shouldBe first.id
        second.newName shouldBe "乙"
        shouldThrow<ApiException> { f.service.submit(f.player, "mai2", "旧名字", "旧名字") }.code shouldBe 400
    }

    "a rejected request leaves the name alone and records the reason" {
        val f = ReviewFixture()
        val req = f.service.submit(f.player, "mai2", "旧名字", "新名字")
        f.service.reject(req.id, 1, "  不合适  ")
        req.status shouldBe NameReviewService.REJECTED
        req.rejectReason shouldBe "不合适"
        f.stored shouldBe "旧名字"
        shouldThrow<ApiException> { f.service.approve(req.id, 1) }.code shouldBe 400
    }

    "requests are approved automatically only after 48 hours" {
        val f = ReviewFixture()
        val req = f.service.submit(f.player, "mai2", "旧名字", "新名字")
        val at = req.submittedAt
        f.service.autoApprove(at + NameReviewService.AUTO_APPROVE_MS - 1)
        req.status shouldBe NameReviewService.PENDING
        f.stored shouldBe "旧名字"
        f.service.autoApprove(at + NameReviewService.AUTO_APPROVE_MS)
        req.status shouldBe NameReviewService.APPROVED
        req.autoApproved shouldBe true
        req.reviewerAuId shouldBe null
        f.stored shouldBe "新名字"
    }

    "approving a request whose profile is gone closes it instead of retrying forever" {
        val f = ReviewFixture()
        val req = f.service.submit(f.player, "mai2", "旧名字", "新名字")
        `when`(f.dataRepo.findByCard(f.player.ghostCard)).thenReturn(null)
        shouldThrow<ApiException> { f.service.approve(req.id, 1) }.code shouldBe 400
        req.status shouldBe NameReviewService.REJECTED
    }

    "reviewed outcomes are shown to the player once, approved or rejected, never while pending" {
        val f = ReviewFixture()
        val a = f.service.submit(f.player, "mai2", "旧名字", "甲")
        f.service.unseen(f.player) shouldBe emptyList()
        f.service.approve(a.id, 1)
        val b = f.service.submit(f.player, "mai2", "甲", "乙")
        f.service.unseen(f.player).map { it.id } shouldBe listOf(a.id)
        f.service.reject(b.id, 1, "不合适")
        f.service.unseen(f.player).map { it.id } shouldBe listOf(a.id, b.id)
        f.service.acknowledge(f.player, listOf(a.id, 999))
        f.service.unseen(f.player).map { it.id } shouldBe listOf(b.id)
        f.service.acknowledge(f.player, listOf(b.id))
        f.service.unseen(f.player) shouldBe emptyList()
    }
})
