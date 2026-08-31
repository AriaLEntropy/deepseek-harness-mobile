package com.example.dsh.dsh

import com.tencent.kuikly.core.pager.PageData

internal val PageData.supportsRelayBridge: Boolean
    get() = isAndroid || isIOS || isOhOs

internal val PageData.supportsSshBridge: Boolean
    get() = isAndroid || isIOS
