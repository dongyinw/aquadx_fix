package icu.samnyan.aqua.sega.maimai2.model

import com.fasterxml.jackson.annotation.JsonIgnore

class Mai2GameEvent {
    @JsonIgnore(false)
    var id = 0L

    var type = 0
    var startDate: String? = "2019-01-01 00:00:00.000000"
    var endDate: String? = "2029-01-01 00:00:00.000000"
    var disableArea = ""
}

class Mai2GameCharge {
    var chargeId = 0L
    var orderId = 0L
    var price = 0
    var startDate: String? = "2019-01-01 00:00:00.000000"
    var endDate: String? = "2029-01-01 00:00:00.000000"
}

class Mai2GamePassCharge {
    var orderId = 0L
    var passTypeId = 0L
    var price = 0
    var startDate: String? = "1970-01-01 00:00:00.0"
    var endDate: String? = "2099-01-01 00:00:00.0"
}

class Mai2NationalData {
    var musicId = 0L
    var nationalDataDetailList: List<Mai2NationalDataDetail> = emptyList()
}

class Mai2NationalDataDetail {
    var level = 0
    var clearRateAp = 0
    var clearRateS = 0
    var clearRateSs = 0
    var clearRateSss = 0
    var clearRateSssPlus = 0
}

class Mai2GameSellingCard {
    var cardId = 0L
    var startDate: String? = "2019-01-01 00:00:00.000000"
    var endDate: String? = "2029-01-01 00:00:00.000000"
    var noticeStartDate: String? = "2019-01-01 00:00:00.000000"
    var noticeEndDate: String? = "2029-01-01 00:00:00.000000"
}
