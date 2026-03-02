package io.gluth.pagespace.ui

import io.gluth.pagespace.domain.Page
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.*
import javax.swing.event.HyperlinkEvent

class ContentPane : JPanel() {

    private val titleLabel: JLabel
    private val bodyPane:   JTextPane
    private val backButton: JButton
    private var navigationListener: NavigationListener? = null

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

        bodyPane.addHyperlinkListener { e ->
            if (e.eventType == HyperlinkEvent.EventType.ACTIVATED && navigationListener != null) {
                val href = e.description
                if (!href.isNullOrBlank()) {
                    navigationListener!!.navigateTo(Page(href, href))
                }
            }
        }

        add(topPanel, BorderLayout.NORTH)
        add(JScrollPane(bodyPane), BorderLayout.CENTER)
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
