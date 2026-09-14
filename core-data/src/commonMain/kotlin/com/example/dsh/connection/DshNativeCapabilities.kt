package com.example.dsh.connection

import com.tencent.kuikly.core.pager.PageData

val PageData.supportsRelayBridge: Boolean
    get() = isAndroid || isIOS || isOhOs

val PageData.supportsSshBridge: Boolean
    get() = isAndroid || isIOS
