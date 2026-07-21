package com.example.data.kgvn

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.core.common.AppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

class KgvnWebSessionManager(
    private val context: Context,
    private val onLog: (String) -> Unit = {}
) {
    private var webView: WebView? = null
    
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.NotStarted)
    val sessionState: StateFlow<SessionState> = _sessionState

    private var activeApiCallback: ((Boolean, String?, String?) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    sealed class SessionState {
        object NotStarted : SessionState()
        object Loading : SessionState()
        data class Active(val campRoleId: String, val msdkToken: String?) : SessionState()
        data class Error(val errorMsg: String) : SessionState()
    }

    fun initWebView(onWebViewReady: (WebView) -> Unit) {
        mainHandler.post {
            val wv = WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                }
                
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onLog("Trang đã tải xong: $url")
                        injectBridge(this@apply)
                    }
                }
                
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun sessionDetected(campRoleId: String, msdkToken: String?) {
                        onLog("Đã phát hiện phiên KGVN: campRoleId=$campRoleId")
                        _sessionState.value = SessionState.Active(campRoleId, msdkToken)
                    }

                    @JavascriptInterface
                    fun apiResponse(action: String, success: Boolean, dataJson: String?, error: String?) {
                        onLog("Nhận phản hồi API [$action]: success=$success")
                        activeApiCallback?.invoke(success, dataJson, error)
                        activeApiCallback = null
                    }

                    @JavascriptInterface
                    fun log(msg: String) {
                        onLog("[WebView] $msg")
                    }
                }, "AovPosterBridge")
            }
            webView = wv
            onWebViewReady(wv)
        }
    }

    fun loadUrl(url: String) {
        _sessionState.value = SessionState.Loading
        mainHandler.post {
            webView?.loadUrl(url)
        }
    }

    fun logout() {
        mainHandler.post {
            CookieManager.getInstance().removeAllCookies(null)
            _sessionState.value = SessionState.NotStarted
            webView?.loadUrl("about:blank")
        }
    }

    private fun injectBridge(wv: WebView) {
        val js = """
            (function() {
                try {
                    AovPosterBridge.log("Bắt đầu cấu trúc Javascript Bridge...");
                    
                    function scanSession() {
                        let campRoleId = "";
                        let msdkToken = "";
                        
                        let cookies = document.cookie.split("; ");
                        for (let c of cookies) {
                            let parts = c.split("=");
                            if (parts.length >= 2) {
                                let k = parts[0].trim();
                                let v = parts[1].trim();
                                if (k === "campRoleId" || k === "roleId" || k === "camp_role_id") campRoleId = v;
                                if (k === "msdkToken" || k === "msdk_token" || k === "token") msdkToken = v;
                            }
                        }
                        
                        for (let i = 0; i < localStorage.length; i++) {
                            let k = localStorage.key(i);
                            if (k.toLowerCase().includes("roleid") || k.toLowerCase().includes("camproleid")) {
                                campRoleId = localStorage.getItem(k);
                            }
                            if (k.toLowerCase().includes("token") || k.toLowerCase().includes("msdk")) {
                                msdkToken = localStorage.getItem(k);
                            }
                        }
                        
                        if (campRoleId) {
                            AovPosterBridge.sessionDetected(campRoleId, msdkToken);
                        }
                    }
                    
                    scanSession();
                    setInterval(scanSession, 3000);
                    
                    let webpackRequire = null;
                    let chunkName = "webpackChunk_kgvn_camp";
                    for (let key in window) {
                        if (key.startsWith("webpackChunk")) {
                            chunkName = key;
                            break;
                        }
                    }
                    
                    window[chunkName] = window[chunkName] || [];
                    try {
                        window[chunkName].push([
                            [Math.random()],
                            {},
                            function(require) {
                                webpackRequire = require;
                            }
                        ]);
                    } catch(e) {
                        AovPosterBridge.log("Lỗi push Webpack Chunk: " + e.message);
                    }
                    
                    window.callAovPosterApi = function(action, params) {
                        AovPosterBridge.log("Gọi API: " + action + " với tham số " + JSON.stringify(params));
                        
                        let apiModule = null;
                        if (webpackRequire) {
                            let cache = webpackRequire.c;
                            if (cache) {
                                for (let id in cache) {
                                    let m = cache[id];
                                    if (m && m.exports) {
                                        let exp = m.exports;
                                        if (exp[action] || (exp.default && exp.default[action])) {
                                            apiModule = exp[action] ? exp : exp.default;
                                            break;
                                        }
                                    }
                                }
                            }
                            
                            if (!apiModule && webpackRequire.m) {
                                for (let id in webpackRequire.m) {
                                    try {
                                        let code = webpackRequire.m[id].toString();
                                        if (code.includes(action)) {
                                            let exp = webpackRequire(id);
                                            if (exp && (exp[action] || (exp.default && exp.default[action]))) {
                                                apiModule = exp[action] ? exp : exp.default;
                                                break;
                                            }
                                        }
                                    } catch(e) {}
                                }
                            }
                        }
                        
                        if (apiModule && typeof apiModule[action] === 'function') {
                            AovPosterBridge.log("Đã tìm thấy Webpack module cho: " + action);
                            apiModule[action](params)
                                .then(res => {
                                    AovPosterBridge.apiResponse(action, true, JSON.stringify(res), null);
                                })
                                .catch(err => {
                                    AovPosterBridge.apiResponse(action, false, null, err.message || JSON.stringify(err));
                                });
                            return;
                        }
                        
                        AovPosterBridge.log("Fallback sử dụng Fetch cho: " + action);
                        let url = "";
                        if (action === "createposter") {
                            url = "/camp/api/poster/create";
                        } else if (action === "getcoscredential") {
                            url = "/camp/api/poster/getcoscredential";
                        } else if (action === "saveposter") {
                            url = "/camp/api/poster/save";
                        } else if (action === "savepostereditinfo") {
                            url = "/camp/api/poster/saveeditinfo";
                        } else {
                            url = "/camp/api/poster/" + action.toLowerCase();
                        }
                        
                        fetch(url, {
                            method: "POST",
                            headers: {
                                "Content-Type": "application/json",
                                "Accept": "application/json"
                            },
                            body: JSON.stringify(params)
                        })
                        .then(response => {
                            if (!response.ok) {
                                throw new Error("HTTP error! status: " + response.status);
                            }
                            return response.json();
                        })
                        .then(data => {
                            AovPosterBridge.apiResponse(action, true, JSON.stringify(data), null);
                        })
                        .catch(err => {
                            AovPosterBridge.apiResponse(action, false, null, err.message);
                        });
                    };
                } catch(e) {
                    AovPosterBridge.log("Lỗi khởi tạo Javascript bridge: " + e.message);
                }
            })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
    }

    /**
     * Executes KGVN API call inside page context.
     */
    fun callApiInPageContext(
        step: Int,
        action: String,
        params: JSONObject,
        callback: (Boolean, String?, String?) -> Unit
    ) {
        if (webView == null) {
            callback(false, null, "WebView chưa được khởi tạo")
            return
        }
        activeApiCallback = callback
        mainHandler.post {
            val js = "window.callAovPosterApi('$action', ${params.toString()});"
            webView?.evaluateJavascript(js, null)
        }
    }
}
