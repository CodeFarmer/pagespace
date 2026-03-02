package io.gluth.pagespace.ui

import io.gluth.pagespace.domain.Page
import java.awt.BorderLayout
import java.awt.Font
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLEditorKit

class ContentPane : JPanel() {

    private val titleLabel: JLabel
    private val bodyPane:   JTextPane
    private val backButton: JButton
    private var navigationListener: NavigationListener? = null
    private var fontSize = 23

    init {
        layout = BorderLayout()

        titleLabel = JLabel("", SwingConstants.CENTER)
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 16f)
        titleLabel.border = BorderFactory.createEmptyBorder(6, 8, 6, 8)

        backButton = JButton("Back")
        backButton.isEnabled = false

        val topPanel = JPanel(BorderLayout())
        topPanel.add(backButton, BorderLayout.WEST)
        topPanel.add(titleLabel, BorderLayout.CENTER)

        bodyPane = JTextPane()
        bodyPane.contentType = "text/html"
        bodyPane.isEditable = false
        // Respect component font for HTML rendering
        bodyPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        applyFont()

        bodyPane.addHyperlinkListener { e ->
            if (e.eventType == HyperlinkEvent.EventType.ACTIVATED && navigationListener != null) {
                val href = e.description
                if (!href.isNullOrBlank()) {
                    navigationListener!!.navigateTo(Page(href, href))
                }
            }
        }

        // Ctrl+= and Ctrl+Shift+= (i.e. Ctrl++) to increase; Ctrl+- to decrease
        val im = bodyPane.getInputMap(WHEN_IN_FOCUSED_WINDOW)
        val am = bodyPane.actionMap
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK),            "font-larger")
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK), "font-larger")
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS,  InputEvent.CTRL_DOWN_MASK),            "font-smaller")
        am.put("font-larger",  object : AbstractAction() { override fun actionPerformed(e: java.awt.event.ActionEvent?) = adjustFontSize(+2) })
        am.put("font-smaller", object : AbstractAction() { override fun actionPerformed(e: java.awt.event.ActionEvent?) = adjustFontSize(-2) })

        add(topPanel, BorderLayout.NORTH)
        add(JScrollPane(bodyPane), BorderLayout.CENTER)
    }

    private fun applyFont() {
        bodyPane.font = Font("SansSerif", Font.PLAIN, fontSize)
        // Also push the size into the stylesheet so <p>, <h1> etc. scale together
        val kit = bodyPane.editorKit as? HTMLEditorKit ?: return
        kit.styleSheet.addRule("body { font-family: SansSerif, Arial, sans-serif; font-size: ${fontSize}pt; }")
        kit.styleSheet.addRule("h1 { font-size: ${fontSize + 6}pt; }")
        kit.styleSheet.addRule("h2 { font-size: ${fontSize + 3}pt; }")
    }

    private fun adjustFontSize(delta: Int) {
        fontSize = (fontSize + delta).coerceIn(8, 36)
        applyFont()
        // Re-set the text so the new stylesheet takes effect
        val saved = bodyPane.text
        bodyPane.text = saved
        bodyPane.caretPosition = 0
    }

    fun setNavigationListener(listener: NavigationListener) {
        navigationListener = listener
    }

    fun setBackAction(action: Runnable) {
        backButton.addActionListener { action.run() }
    }

    fun setContent(page: Page, htmlBody: String) {
        titleLabel.text = page.title
        bodyPane.text = htmlBody
        bodyPane.caretPosition = 0
        backButton.isEnabled = true
    }

    fun setLoading(title: String) {
        titleLabel.text = "Loading $title\u2026"
        bodyPane.text = "<html><body><i>Loading\u2026</i></body></html>"
        backButton.isEnabled = false
    }

    fun currentPageTitle(): String = titleLabel.text
}
