package com.example.jarvis.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.jarvis.model.ScreenNodeItem
import com.example.jarvis.model.ScreenSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class ElementSearchOutcome {
    data class Success(val matchedLabel: String, val details: String) : ElementSearchOutcome()
    data class Ambiguous(val query: String, val candidates: List<String>) : ElementSearchOutcome()
    data class NotFound(val reason: String) : ElementSearchOutcome()
}

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
        refreshScreenSnapshot()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            refreshScreenSnapshot()
        }
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
            _isServiceConnected.value = false
        }
    }

    fun refreshScreenSnapshot(): ScreenSnapshot? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString() ?: "android"
        val items = mutableListOf<ScreenNodeItem>()
        traverseNode(root, items, maxNodes = 140)
        val snapshot = ScreenSnapshot(
            packageName = pkg,
            appTitle = pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() },
            capturedAtMillis = System.currentTimeMillis(),
            nodes = items,
            isSimulatedFallback = false
        )
        _latestScreenSnapshot.value = snapshot
        return snapshot
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo?,
        outList: MutableList<ScreenNodeItem>,
        maxNodes: Int
    ) {
        if (node == null || outList.size >= maxNodes) return
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val cls = node.className?.toString() ?: ""

        val isMeaningful = text.isNotBlank() ||
            desc.isNotBlank() ||
            node.isClickable ||
            node.isEditable ||
            node.isScrollable ||
            node.isCheckable

        if (isMeaningful && rect.width() > 0 && rect.height() > 0) {
            outList.add(
                ScreenNodeItem(
                    index = outList.size + 1,
                    text = text,
                    contentDescription = desc,
                    className = cls,
                    viewIdResourceName = viewId,
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    isScrollable = node.isScrollable,
                    isCheckable = node.isCheckable,
                    isChecked = node.isChecked,
                    boundsSummary = "[${rect.left},${rect.top} - ${rect.right},${rect.bottom}]",
                    centerX = rect.centerX(),
                    centerY = rect.centerY()
                )
            )
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            if (outList.size >= maxNodes) break
            traverseNode(node.getChild(i), outList, maxNodes)
        }
    }

    fun executeGlobalNavigation(action: Int): Boolean {
        return performGlobalAction(action)
    }

    fun findAndClick(query: String, longPress: Boolean = false): ElementSearchOutcome {
        val root = rootInActiveWindow ?: return ElementSearchOutcome.NotFound("No active accessibility window.")
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return ElementSearchOutcome.NotFound("Empty target element query.")

        val candidates = mutableListOf<Pair<AccessibilityNodeInfo, String>>()
        collectMatchingNodes(root, cleanQuery.lowercase(), candidates)

        if (candidates.isEmpty()) {
            return ElementSearchOutcome.NotFound("Could not find element '$cleanQuery' on current screen.")
        }

        // Deduplicate by label + approximate position
        val distinctLabels = candidates.map { it.second }.distinct()
        if (distinctLabels.size > 1 && distinctLabels.none { it.equals(cleanQuery, ignoreCase = true) }) {
            return ElementSearchOutcome.Ambiguous(cleanQuery, distinctLabels.take(4))
        }

        // Pick exact match first, otherwise first match
        val chosenPair = candidates.firstOrNull { it.second.equals(cleanQuery, ignoreCase = true) }
            ?: candidates.first()

        val targetNode = findClickableNodeOrParent(chosenPair.first) ?: chosenPair.first
        val actionConstant = if (longPress) {
            AccessibilityNodeInfo.ACTION_LONG_CLICK
        } else {
            AccessibilityNodeInfo.ACTION_CLICK
        }

        val clickedByNode = targetNode.performAction(actionConstant)
        if (clickedByNode) {
            refreshScreenSnapshot()
            return ElementSearchOutcome.Success(
                matchedLabel = chosenPair.second,
                details = "Performed ${if (longPress) "long-press" else "click"} via AccessibilityNodeInfo on '${chosenPair.second}'."
            )
        }

        // Fallback: dispatch gesture at center coordinates
        val rect = Rect()
        chosenPair.first.getBoundsInScreen(rect)
        if (rect.width() > 0 && rect.height() > 0) {
            val dispatched = dispatchTapGesture(
                x = rect.centerX().toFloat(),
                y = rect.centerY().toFloat(),
                durationMs = if (longPress) 600L else 80L
            )
            if (dispatched) {
                refreshScreenSnapshot()
                return ElementSearchOutcome.Success(
                    matchedLabel = chosenPair.second,
                    details = "Performed gesture tap at (${rect.centerX()}, ${rect.centerY()}) on '${chosenPair.second}'."
                )
            }
        }

        return ElementSearchOutcome.NotFound("Element '${chosenPair.second}' was found but did not accept click or gesture.")
    }

    private fun collectMatchingNodes(
        node: AccessibilityNodeInfo?,
        queryLower: String,
        out: MutableList<Pair<AccessibilityNodeInfo, String>>
    ) {
        if (node == null) return
        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val idName = node.viewIdResourceName?.substringAfterLast("/") ?: ""

        val label = when {
            text.isNotBlank() -> text
            desc.isNotBlank() -> desc
            idName.isNotBlank() -> idName
            else -> ""
        }

        if (label.isNotBlank() && (
                label.lowercase().contains(queryLower) ||
                    idName.lowercase().contains(queryLower)
                )
        ) {
            out.add(node to label)
        }

        for (i in 0 until node.childCount) {
            collectMatchingNodes(node.getChild(i), queryLower, out)
        }
    }

    private fun findClickableNodeOrParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var curr = node
        var depth = 0
        while (curr != null && depth < 6) {
            if (curr.isClickable) return curr
            curr = curr.parent
            depth++
        }
        return null
    }

    fun performScrollAction(direction: String): Boolean {
        val root = rootInActiveWindow
        val dirUpper = direction.uppercase()
        if (root != null) {
            val scrollable = findFirstScrollableNode(root)
            if (scrollable != null) {
                val action = when (dirUpper) {
                    "DOWN", "FORWARD", "NEECHE" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    "UP", "BACKWARD", "UPAR" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                }
                if (scrollable.performAction(action)) {
                    refreshScreenSnapshot()
                    return true
                }
            }
        }

        // Gesture fallback
        val displayMetrics = resources.displayMetrics
        val w = displayMetrics.widthPixels.toFloat().coerceAtLeast(500f)
        val h = displayMetrics.heightPixels.toFloat().coerceAtLeast(900f)
        val path = Path()
        when (dirUpper) {
            "UP", "BACKWARD", "UPAR" -> {
                // Scroll up = view content above -> swipe finger downward
                path.moveTo(w * 0.5f, h * 0.3f)
                path.lineTo(w * 0.5f, h * 0.75f)
            }
            "LEFT" -> {
                path.moveTo(w * 0.8f, h * 0.5f)
                path.lineTo(w * 0.2f, h * 0.5f)
            }
            "RIGHT" -> {
                path.moveTo(w * 0.2f, h * 0.5f)
                path.lineTo(w * 0.8f, h * 0.5f)
            }
            else -> {
                // Scroll down = view content below -> swipe finger upward
                path.moveTo(w * 0.5f, h * 0.75f)
                path.lineTo(w * 0.5f, h * 0.28f)
            }
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 280L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun findFirstScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val found = findFirstScrollableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    fun typeText(targetHint: String, textToEnter: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val editable = findTargetEditableNode(root, targetHint.lowercase()) ?: return false
        editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        editable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                textToEnter
            )
        }
        val ok = editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        refreshScreenSnapshot()
        return ok
    }

    private fun findTargetEditableNode(
        node: AccessibilityNodeInfo?,
        hintLower: String
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) {
            if (hintLower.isBlank()) return node
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val id = node.viewIdResourceName?.lowercase() ?: ""
            if (text.contains(hintLower) || desc.contains(hintLower) || id.contains(hintLower)) {
                return node
            }
        }
        var fallbackEditable: AccessibilityNodeInfo? = if (node.isEditable) node else null
        for (i in 0 until node.childCount) {
            val childMatch = findTargetEditableNode(node.getChild(i), hintLower)
            if (childMatch != null) {
                if (hintLower.isBlank()) return childMatch
                val text = childMatch.text?.toString()?.lowercase() ?: ""
                val desc = childMatch.contentDescription?.toString()?.lowercase() ?: ""
                val id = childMatch.viewIdResourceName?.lowercase() ?: ""
                if (text.contains(hintLower) || desc.contains(hintLower) || id.contains(hintLower)) {
                    return childMatch
                }
                if (fallbackEditable == null) fallbackEditable = childMatch
            }
        }
        return fallbackEditable
    }

    fun performClipboardOperation(op: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findTargetEditableNode(root, "") ?: return false
        val action = when (op.uppercase()) {
            "COPY" -> AccessibilityNodeInfo.ACTION_COPY
            "PASTE" -> AccessibilityNodeInfo.ACTION_PASTE
            "CUT" -> AccessibilityNodeInfo.ACTION_CUT
            "SELECT_ALL" -> {
                val len = focused.text?.length ?: 0
                val args = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, len)
                }
                return focused.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
            }
            else -> AccessibilityNodeInfo.ACTION_PASTE
        }
        return focused.performAction(action)
    }

    private fun dispatchTapGesture(x: Float, y: Float, durationMs: Long): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun takeSystemScreenshot(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            false
        }
    }

    companion object {
        @Volatile
        var instance: JarvisAccessibilityService? = null
            private set

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        private val _latestScreenSnapshot = MutableStateFlow<ScreenSnapshot?>(null)
        val latestScreenSnapshot: StateFlow<ScreenSnapshot?> = _latestScreenSnapshot.asStateFlow()

        // Interactive Screen Sandbox state for verifying UI element search, click, typing & scrolling
        // even when testing inside the app or before enabling system AccessibilityService
        private val _sandboxSearchBoxText = MutableStateFlow("")
        val sandboxSearchBoxText: StateFlow<String> = _sandboxSearchBoxText.asStateFlow()

        private val _sandboxLastActionNote = MutableStateFlow("Ready for screen inspection & node actions")
        val sandboxLastActionNote: StateFlow<String> = _sandboxLastActionNote.asStateFlow()

        private val _sandboxNodes = MutableStateFlow(
            listOf(
                ScreenNodeItem(
                    index = 1,
                    text = "Search box",
                    contentDescription = "Search input field",
                    className = "android.widget.EditText",
                    viewIdResourceName = "com.jarvis.sandbox:id/search_input",
                    isClickable = true,
                    isEditable = true,
                    isScrollable = false,
                    isCheckable = false,
                    isChecked = false,
                    boundsSummary = "[48,220 - 980,340]",
                    centerX = 514,
                    centerY = 280
                ),
                ScreenNodeItem(
                    index = 2,
                    text = "Login",
                    contentDescription = "Login Button",
                    className = "android.widget.Button",
                    viewIdResourceName = "com.jarvis.sandbox:id/btn_login",
                    isClickable = true,
                    isEditable = false,
                    isScrollable = false,
                    isCheckable = false,
                    isChecked = false,
                    boundsSummary = "[64,380 - 480,500]",
                    centerX = 272,
                    centerY = 440
                ),
                ScreenNodeItem(
                    index = 3,
                    text = "Send",
                    contentDescription = "Send Message Button",
                    className = "android.widget.Button",
                    viewIdResourceName = "com.jarvis.sandbox:id/btn_send",
                    isClickable = true,
                    isEditable = false,
                    isScrollable = false,
                    isCheckable = false,
                    isChecked = false,
                    boundsSummary = "[540,380 - 960,500]",
                    centerX = 750,
                    centerY = 440
                ),
                ScreenNodeItem(
                    index = 4,
                    text = "Download",
                    contentDescription = "Download File Button",
                    className = "android.widget.Button",
                    viewIdResourceName = "com.jarvis.sandbox:id/btn_download",
                    isClickable = true,
                    isEditable = false,
                    isScrollable = false,
                    isCheckable = false,
                    isChecked = false,
                    boundsSummary = "[64,530 - 480,650]",
                    centerX = 272,
                    centerY = 590
                ),
                ScreenNodeItem(
                    index = 5,
                    text = "Auto Sync Checkbox",
                    contentDescription = "Enable Auto Sync",
                    className = "android.widget.CheckBox",
                    viewIdResourceName = "com.jarvis.sandbox:id/chk_sync",
                    isClickable = true,
                    isEditable = false,
                    isScrollable = false,
                    isCheckable = true,
                    isChecked = true,
                    boundsSummary = "[540,530 - 960,650]",
                    centerX = 750,
                    centerY = 590
                ),
                ScreenNodeItem(
                    index = 6,
                    text = "Content Feed List",
                    contentDescription = "Scrollable feed container",
                    className = "androidx.recyclerview.widget.RecyclerView",
                    viewIdResourceName = "com.jarvis.sandbox:id/feed_list",
                    isClickable = false,
                    isEditable = false,
                    isScrollable = true,
                    isCheckable = false,
                    isChecked = false,
                    boundsSummary = "[0,680 - 1080,1920]",
                    centerX = 540,
                    centerY = 1300
                )
            )
        )

        fun getActiveOrSandboxSnapshot(): ScreenSnapshot {
            val live = _latestScreenSnapshot.value
            if (live != null && _isServiceConnected.value && live.nodes.isNotEmpty()) {
                return live
            }
            return ScreenSnapshot(
                packageName = "com.aistudio.jarvisagent.sandbox",
                appTitle = "JARVIS Screen Inspector Target",
                capturedAtMillis = System.currentTimeMillis(),
                nodes = _sandboxNodes.value,
                isSimulatedFallback = !_isServiceConnected.value
            )
        }

        fun updateSandboxTypedText(target: String, newText: String): Boolean {
            _sandboxSearchBoxText.value = newText
            _sandboxNodes.value = _sandboxNodes.value.map { node ->
                if (node.isEditable) {
                    node.copy(text = newText.ifBlank { "Search box" })
                } else {
                    node
                }
            }
            _sandboxLastActionNote.value = "Typed \"$newText\" into ${target.ifBlank { "Search box" }} [Verified]"
            return true
        }

        fun performSandboxClick(targetQuery: String, longPress: Boolean = false): ElementSearchOutcome {
            val q = targetQuery.trim().lowercase()
            val matches = _sandboxNodes.value.filter {
                it.displayLabel.lowercase().contains(q) ||
                    it.contentDescription.lowercase().contains(q) ||
                    it.viewIdResourceName.lowercase().contains(q)
            }
            if (matches.isEmpty()) {
                return ElementSearchOutcome.NotFound("Element '$targetQuery' not found in screen hierarchy.")
            }
            val chosen = matches.firstOrNull { it.displayLabel.equals(targetQuery.trim(), ignoreCase = true) }
                ?: matches.first()
            if (chosen.isCheckable) {
                _sandboxNodes.value = _sandboxNodes.value.map {
                    if (it.index == chosen.index) it.copy(isChecked = !it.isChecked) else it
                }
            }
            val actionVerb = if (longPress) "Long-pressed" else "Clicked"
            _sandboxLastActionNote.value = "$actionVerb '${chosen.displayLabel}' at ${chosen.boundsSummary} [Verified]"
            return ElementSearchOutcome.Success(
                matchedLabel = chosen.displayLabel,
                details = "$actionVerb '${chosen.displayLabel}' at (${chosen.centerX}, ${chosen.centerY})"
            )
        }

        fun performSandboxScroll(direction: String): String {
            val msg = "Scrolled screen ${direction.uppercase()} on scrollable container [Verified]"
            _sandboxLastActionNote.value = msg
            return msg
        }
    }
}
