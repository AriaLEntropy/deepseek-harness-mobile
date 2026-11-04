package com.example.dsh.ui

/**
 * 顶部安全区
 *
 * 挖孔/刘海机上 statusBarHeight 只报系统状态栏本身，不含摄像头挖孔；
 * 页面首屏如果只按它留白，内容会顶进状态栏。取 safeAreaInsets.top
 * 与 statusBarHeight 的较大值，首页与对话页共用这一处判定。
 */
internal fun dshTopInset(statusBarHeight: Float, safeTop: Float): Float =
    maxOf(statusBarHeight, safeTop)

/*
 * 底部安全区契约：内容画到物理底边，谁贴底谁自己让出 safeAreaInsets.bottom
 *
 * 宿主 KuiklyRenderActivity.setupImmersiveMode 做的是整屏沉浸（LAYOUT_FULLSCREEN +
 * LAYOUT_STABLE + LAYOUT_HIDE_NAVIGATION，导航栏透明），所以 pageViewHeight 含导航栏那
 * 一条，页面与弹层可以一直铺到屏幕底边。代价是贴底的内容会被系统手势条压住，因此：
 *  - 贴底的控件统一取「设计值与 inset 的较大者」：maxOf(设计值, pagerData.safeAreaInsets.bottom)。
 *    没导航栏时就是设计值（不会凭空空出一格），有三键/手势条时被挤到栏上方
 *    （输入条、首页悬浮栏、BusSheetPanel 与 BusSettingsOverlay 的尾部占位片都这么做）
 *  - 设计稿标注「离屏幕底边 N」的（对话页输入条 26）：那是从物理底边量的，不要再减 inset
 *  - 纯装饰的贴底层（首页底部渐隐）仍锚 bottom(0) 铺满，不参与取大者
 *  - 绝对定位的贴底装饰（首页底部渐隐、悬浮胶囊栏）：锚到物理底边就符合设计稿，无需换算
 *
 * 历史坑：早期宿主只沉浸到状态栏，内容区被系统停在导航栏上方，那时再叠加 inset 会凭空多出
 * 一条同色横条；改成整屏沉浸之后横条没了，「最后一项被切成硬边」改为靠留白解决
 */
