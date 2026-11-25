package com.myanmar.ledger2d.feature.main

/** Weekly targets are not triggered birth instances. Keep the existing UI grouping. */
internal fun isCherryWeeklyWatch(id:String):Boolean =
    id in setOf("s3-week","s3-six","new-groups-weekly-nine",
        "new-groups-weekly-break","new-groups-weekly-wheel")
