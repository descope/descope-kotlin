package com.descope.android

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebView
import com.descope.sdk.DescopeFlow
import com.descope.sdk.DescopeSdk
import com.descope.types.AuthenticationResponse
import com.descope.types.DescopeException

class FlowView : ViewGroup {

    var flowPresentation: DescopeFlow.Presentation?
        get() = flowController.flowPresentation
        set(value) { flowController.flowPresentation = value }
    var onReadyCallback: (() -> Unit)?
        get() = flowController.onReadyCallback
        set(value) { flowController.onReadyCallback = value }
    var onErrorCallback: ((DescopeException) -> Unit)?
        get() = flowController.onErrorCallback
        set(value) { flowController.onErrorCallback = value }
    var onSuccessCallback: ((AuthenticationResponse) -> Unit)?
        get() = flowController.onSuccessCallback
        set(value) { flowController.onSuccessCallback = value }
    var descopeSdk: DescopeSdk?
        get() = flowController.descopeSdk
        set(value) { flowController.descopeSdk = value }

    private lateinit var flowController: FlowController

    constructor(context: Context) : super(context, null, 0) {
        initView()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs, 0) {
        initView()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initView()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        initView()
    }

    private fun initView() {
        val webView = WebView(context)
        addView(webView, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        this.flowController = FlowController(webView)
    }
    
    // API

    fun startFlow(flowUrl: String) {
        flowController.startFlow(flowUrl)
    }

    fun resumeFromDeepLink(incomingUriString: String) {
        flowController.resumeFromDeepLink(incomingUriString)
    }

    // Internal 

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.layout(0, 0, width, height);
        }
    }
}

// Compose

//@Composable
//fun Flow(flowUrl: String) {
//    AndroidView(
//        factory = { FlowView(it) },
//        update = { it.startFlow(flowUrl) }
//    )
//}
