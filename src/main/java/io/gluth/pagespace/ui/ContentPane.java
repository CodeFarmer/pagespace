package io.gluth.pagespace.ui;

import io.gluth.pagespace.domain.Page;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;

public class ContentPane extends JPanel {

    private final JLabel titleLabel;
    private final JTextPane bodyPane;
    private final JButton backButton;
    private NavigationListener navigationListener;

    public ContentPane() {
        setLayout(new BorderLayout());

        titleLabel = new JLabel("", SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        backButton = new JButton("Back");
        backButton.setEnabled(false);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(backButton, BorderLayout.WEST);
        topPanel.add(titleLabel, BorderLayout.CENTER);

        bodyPane = new JTextPane();
        bodyPane.setContentType("text/html");
        bodyPane.setEditable(false);

        bodyPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED
                    && navigationListener != null) {
                String href = e.getDescription();
                if (href != null && !href.isBlank()) {
                    navigationListener.navigateTo(new Page(href, href));
                }
            }
        });

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(bodyPane), BorderLayout.CENTER);
    }

    public void setNavigationListener(NavigationListener listener) {
        this.navigationListener = listener;
    }

    public void setBackAction(Runnable action) {
        backButton.addActionListener(e -> action.run());
    }

    public void setContent(Page page, String htmlBody) {
        titleLabel.setText(page.title());
        bodyPane.setText(htmlBody);
        bodyPane.setCaretPosition(0);
        backButton.setEnabled(true);
    }

    public String currentPageTitle() {
        return titleLabel.getText();
    }
}
