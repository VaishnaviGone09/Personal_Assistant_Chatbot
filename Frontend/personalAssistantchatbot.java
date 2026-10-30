
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;

public class PersonalAssistantChatbot extends JFrame {

    private final Color BG = new Color(15, 20, 38);
    private final Color PANEL = new Color(26, 34, 57);
    private final Color PURPLE = new Color(139, 92, 246);
    private final Color WHITE = new Color(245, 245, 255);
    private final Color MUTED = new Color(160, 170, 195);
    private final Color GREEN = new Color(52, 211, 153);

    private CardLayout cards;
    private JPanel content;
    private JPanel chatMessages;
    private JTextField chatInput;
    private JLabel connectionStatus;
    private JLabel dateLabel;
    private JLabel taskCountLabel;
    private JLabel completedCountLabel;

    private final List<Task> tasks = new ArrayList<>();
    private final HttpClient client = HttpClient.newHttpClient();

    private YearMonth calendarMonth = YearMonth.now();
    private LocalDate selectedDate = LocalDate.now();

    private static class Task {
        String name;
        LocalDate date;
        String priority;
        boolean completed;

        Task(String name, LocalDate date, String priority) {
            this.name = name;
            this.date = date;
            this.priority = priority;
        }
    }

    public PersonalAssistantChatbot() {
        setTitle("AURA - Personal Assistant");
        setSize(1250, 790);
        setMinimumSize(new Dimension(950, 650));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        tasks.add(new Task("Complete Java PBL project", LocalDate.now(), "High"));
        tasks.add(new Task("Review college assignments", LocalDate.now(), "Medium"));
        tasks.add(new Task("Prepare presentation", LocalDate.now().plusDays(1), "High"));
        tasks.add(new Task("Revise programming concepts", LocalDate.now().plusDays(2), "Low"));

        setLayout(new BorderLayout());
        getContentPane().setBackground(BG);

        createSidebar();

        cards = new CardLayout();
        content = new JPanel(cards);
        content.setBackground(BG);

        content.add(createDashboard(), "Dashboard");
        content.add(createCalendarPage(), "Calendar");
        content.add(createTasksPage(), "Tasks");
        content.add(createAssistantPage(), "Assistant");
        content.add(createBriefingPage(), "Briefing");

        add(content, BorderLayout.CENTER);

        checkBackend();
        setVisible(true);
    }

    private void createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setPreferredSize(new Dimension(235, 0));
        sidebar.setBackground(new Color(11, 16, 31));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(new EmptyBorder(27, 17, 20, 17));

        JLabel logo = new JLabel("✦ AURA");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 29));
        logo.setForeground(Color.WHITE);

        JLabel sub = new JLabel("YOUR PERSONAL ASSISTANT");
        sub.setForeground(MUTED);
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 9));

        sidebar.add(logo);
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(sub);
        sidebar.add(Box.createVerticalStrut(40));

        addSection(sidebar, "WORKSPACE");
        addNavigation(sidebar, "⌂", "Dashboard");
        addNavigation(sidebar, "▦", "Calendar");
        addNavigation(sidebar, "✓", "Tasks");
        addNavigation(sidebar, "✧", "Assistant");

        sidebar.add(Box.createVerticalStrut(28));
        addSection(sidebar, "YOUR DAY");
        addNavigation(sidebar, "◷", "Briefing");

        sidebar.add(Box.createVerticalGlue());

        JPanel profile = new JPanel(new BorderLayout(10, 0));
        profile.setBackground(PANEL);
        profile.setBorder(new EmptyBorder(12, 10, 12, 10));

        JLabel avatar = new JLabel("V");
        avatar.setOpaque(true);
        avatar.setBackground(PURPLE);
        avatar.setForeground(Color.WHITE);
        avatar.setHorizontalAlignment(SwingConstants.CENTER);
        avatar.setPreferredSize(new Dimension(35, 35));
        avatar.setFont(new Font("Segoe UI", Font.BOLD, 17));

        JLabel user = new JLabel("<html><b>Vaishnavi</b><br><font color='#A0AAC3'>Personal workspace</font></html>");
        user.setForeground(WHITE);

        profile.add(avatar, BorderLayout.WEST);
        profile.add(user, BorderLayout.CENTER);
        sidebar.add(profile);

        add(sidebar, BorderLayout.WEST);
    }

    private void addSection(JPanel panel, String name) {
        JLabel label = new JLabel(name);
        label.setForeground(MUTED);
        label.setFont(new Font("Segoe UI", Font.BOLD, 10));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createVerticalStrut(12));
    }

    private void addNavigation(JPanel panel, String icon, String name) {
        JButton button = new JButton(icon + "    " + name);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setForeground(WHITE);
        button.setBackground(new Color(11, 16, 31));
        button.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(13, 12, 13, 8));
        button.setMaximumSize(new Dimension(210, 48));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);

        button.addActionListener(e -> {
            cards.show(content, name);
            if (name.equals("Calendar")) refreshCalendar();
            if (name.equals("Tasks")) refreshTasks();
            if (name.equals("Dashboard")) refreshDashboard();
        });

        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(55, 44, 91));
            }
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(11, 16, 31));
            }
        });

        panel.add(button);
        panel.add(Box.createVerticalStrut(5));
    }

    private JPanel page(String title, String subtitle) {
        JPanel page = new JPanel(new BorderLayout(18, 18));
        page.setBackground(BG);
        page.setBorder(new EmptyBorder(25, 27, 23, 27));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel heading = new JLabel(title);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        heading.setForeground(WHITE);

        JLabel description = new JLabel(subtitle);
        description.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        description.setForeground(MUTED);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(heading);
        titles.add(Box.createVerticalStrut(5));
        titles.add(description);

        header.add(titles, BorderLayout.WEST);
        page.add(header, BorderLayout.NORTH);
        return page;
    }

    private JPanel createDashboard() {
        JPanel page = page("Dashboard ✨", "Your day, organised in one place.");

        JPanel main = new JPanel(new BorderLayout(15, 15));
        main.setOpaque(false);

        JPanel hero = new JPanel();
        hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
        hero.setBackground(new Color(62, 48, 116));
        hero.setBorder(new EmptyBorder(24, 25, 24, 25));

        JLabel greeting = new JLabel("Hello, Vaishnavi! 👋");
        greeting.setForeground(Color.WHITE);
        greeting.setFont(new Font("Segoe UI", Font.BOLD, 27));

        dateLabel = new JLabel(LocalDate.now().format(
                DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")));
        dateLabel.setForeground(new Color(222, 215, 255));
        dateLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JLabel quote = new JLabel("Let's make today productive and stress-free.");
        quote.setForeground(Color.WHITE);
        quote.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        hero.add(greeting);
        hero.add(Box.createVerticalStrut(7));
        hero.add(dateLabel);
        hero.add(Box.createVerticalStrut(14));
        hero.add(quote);

        main.add(hero, BorderLayout.NORTH);

        JPanel stats = new JPanel(new GridLayout(1, 3, 13, 0));
        stats.setOpaque(false);

        taskCountLabel = new JLabel();
        completedCountLabel = new JLabel();

        stats.add(statCard("TOTAL TASKS", taskCountLabel, "▣", PURPLE));
        stats.add(statCard("COMPLETED", completedCountLabel, "✓", GREEN));
        stats.add(statCard("UPCOMING EVENTS", new JLabel("2"), "◷",
                new Color(251, 191, 36)));

        JPanel lower = new JPanel(new GridLayout(1, 2, 13, 0));
        lower.setOpaque(false);

        JPanel upcoming = sectionPanel("Today's tasks");
        JPanel taskList = new JPanel();
        taskList.setOpaque(false);
        taskList.setLayout(new BoxLayout(taskList, BoxLayout.Y_AXIS));

        for (Task task : tasks) {
            if (task.date.equals(LocalDate.now())) {
                taskList.add(taskRow(task));
            }
        }

        upcoming.add(taskList, BorderLayout.CENTER);

        JPanel quick = sectionPanel("Quick actions");
        JPanel buttons = new JPanel(new GridLayout(3, 1, 8, 8));
        buttons.setOpaque(false);

        buttons.add(quickButton("＋  Add a new task", e -> addTaskDialog()));
        buttons.add(quickButton("▦  Open calendar",
                e -> cards.show(content, "Calendar")));
        buttons.add(quickButton("✧  Talk to AURA",
                e -> cards.show(content, "Assistant")));

        quick.add(buttons, BorderLayout.CENTER);

        lower.add(upcoming);
        lower.add(quick);

        JPanel body = new JPanel(new BorderLayout(14, 14));
        body.setOpaque(false);
        body.add(stats, BorderLayout.NORTH);
        body.add(lower, BorderLayout.CENTER);

        main.add(body, BorderLayout.CENTER);
        page.add(main, BorderLayout.CENTER);

        refreshDashboard();
        return page;
    }

    private JPanel statCard(String title, JLabel value, String icon, Color color) {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBackground(PANEL);
        card.setBorder(new EmptyBorder(16, 17, 16, 17));

        JLabel heading = new JLabel(icon + "  " + title);
        heading.setForeground(MUTED);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 10));

        value.setForeground(color);
        value.setFont(new Font("Segoe UI", Font.BOLD, 27));

        card.add(heading, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private JPanel sectionPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout(10, 12));
        panel.setBackground(PANEL);
        panel.setBorder(new EmptyBorder(17, 17, 17, 17));

        JLabel heading = new JLabel(title);
        heading.setForeground(WHITE);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 15));

        panel.add(heading, BorderLayout.NORTH);
        return panel;
    }

    private JPanel taskRow(Task task) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(9, 3, 9, 3));

        JCheckBox check = new JCheckBox();
        check.setOpaque(false);
        check.setSelected(task.completed);
        check.addActionListener(e -> {
            task.completed = check.isSelected();
            refreshDashboard();
            refreshTasks();
        });

        JLabel name = new JLabel(task.name);
        name.setForeground(task.completed ? MUTED : WHITE);
        name.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JLabel priority = new JLabel(task.priority);
        priority.setForeground(task.priority.equals("High")
                ? new Color(248, 113, 113) : MUTED);
        priority.setFont(new Font("Segoe UI", Font.BOLD, 10));

        row.add(check, BorderLayout.WEST);
        row.add(name, BorderLayout.CENTER);
        row.add(priority, BorderLayout.EAST);
        return row;
    }

    private JButton quickButton(String text, ActionListener action) {
        JButton button = new JButton(text);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setBackground(new Color(42, 52, 78));
        button.setForeground(WHITE);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(12, 12, 12, 12));
        button.addActionListener(action);
        return button;
    }

    private JPanel createCalendarPage() {
        JPanel page = page("Calendar ▦", "View dates and organise your schedule.");

        JPanel calendar = new JPanel(new BorderLayout(10, 12));
        calendar.setBackground(PANEL);
        calendar.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel monthHeader = new JPanel(new BorderLayout());
        monthHeader.setOpaque(false);

        JButton previous = quickButton("←", e -> {
            calendarMonth = calendarMonth.minusMonths(1);
            refreshCalendar();
        });

        JButton next = quickButton("→", e -> {
            calendarMonth = calendarMonth.plusMonths(1);
            refreshCalendar();
        });

        JLabel monthName = new JLabel();
        monthName.setForeground(WHITE);
        monthName.setFont(new Font("Segoe UI", Font.BOLD, 19));
        monthName.setHorizontalAlignment(SwingConstants.CENTER);
        monthName.setName("monthName");

        JPanel monthButtons = new JPanel(new BorderLayout(8, 0));
        monthButtons.setOpaque(false);
        monthButtons.add(previous, BorderLayout.WEST);
        monthButtons.add(next, BorderLayout.EAST);

        monthHeader.add(monthButtons, BorderLayout.WEST);
        monthHeader.add(monthName, BorderLayout.CENTER);

        calendar.add(monthHeader, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 7, 5, 5));
        grid.setOpaque(false);
        grid.setName("calendarGrid");

        calendar.add(grid, BorderLayout.CENTER);

        JLabel selected = new JLabel();
        selected.setForeground(new Color(196, 181, 253));
        selected.setFont(new Font("Segoe UI", Font.BOLD, 13));
        selected.setName("selectedDate");

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);

        bottom.add(selected, BorderLayout.WEST);
        bottom.add(quickButton("+ Add event / task",
                e -> addTaskDialog()), BorderLayout.EAST);

        calendar.add(bottom, BorderLayout.SOUTH);

        page.add(calendar, BorderLayout.CENTER);
        refreshCalendar();
        return page;
    }

    private void refreshCalendar() {
        if (content == null) return;

        JPanel calendar = findNamedPanel(content, "calendarGrid");
        if (calendar == null) return;

        JLabel monthName = findNamedLabel(content, "monthName");
        JLabel selected = findNamedLabel(content, "selectedDate");

        if (monthName != null) {
            monthName.setText(calendarMonth.format(
                    DateTimeFormatter.ofPattern("MMMM yyyy")));
        }

        if (selected != null) {
            selected.setText("Selected: " + selectedDate.format(
                    DateTimeFormatter.ofPattern("dd MMMM yyyy")));
        }

        calendar.removeAll();

        String[] days = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};

        for (String day : days) {
            JLabel label = new JLabel(day, SwingConstants.CENTER);
            label.setForeground(MUTED);
            label.setFont(new Font("Segoe UI", Font.BOLD, 11));
            calendar.add(label);
        }

        LocalDate first = calendarMonth.atDay(1);
        int offset = first.getDayOfWeek().getValue() % 7;

        for (int i = 0; i < offset; i++) {
            calendar.add(new JLabel(""));
        }

        for (int day = 1; day <= calendarMonth.lengthOfMonth(); day++) {
            final int selectedDay = day;
            LocalDate date = calendarMonth.atDay(day);

            JButton button = new JButton(String.valueOf(day));
            button.setFocusPainted(false);
            button.setFont(new Font("Segoe UI", Font.BOLD, 12));
            button.setForeground(WHITE);
            button.setBackground(date.equals(selectedDate)
                    ? PURPLE : new Color(40, 50, 76));
            button.setBorder(new EmptyBorder(12, 4, 12, 4));

            boolean hasTask = tasks.stream().anyMatch(t -> t.date.equals(date));
            if (hasTask) {
                button.setText(day + " •");
            }

            button.addActionListener(e -> {
                selectedDate = calendarMonth.atDay(selectedDay);
                refreshCalendar();
            });

            calendar.add(button);
        }

        calendar.revalidate();
        calendar.repaint();
    }

    private JPanel findNamedPanel(Container root, String name) {
        for (Component c : root.getComponents()) {
            if (c instanceof JPanel && name.equals(c.getName())) {
                return (JPanel) c;
            }
            if (c instanceof Container) {
                JPanel result = findNamedPanel((Container) c, name);
                if (result != null) return result;
            }
        }
        return null;
    }

    private JLabel findNamedLabel(Container root, String name) {
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel && name.equals(c.getName())) {
                return (JLabel) c;
            }
            if (c instanceof Container) {
                JLabel result = findNamedLabel((Container) c, name);
                if (result != null) return result;
            }
        }
        return null;
    }

    private JPanel createTasksPage() {
        JPanel page = page("My Tasks ✓", "Add, track and complete your tasks.");

        JPanel panel = sectionPanel("Your task list");
        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setName("tasksList");

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(new JLabel(""), BorderLayout.CENTER);
        top.add(quickButton("+ Add task", e -> addTaskDialog()),
                BorderLayout.EAST);

        JPanel wrapper = new JPanel(new BorderLayout(10, 15));
        wrapper.setOpaque(false);
        wrapper.add(top, BorderLayout.NORTH);
        wrapper.add(list, BorderLayout.CENTER);

        panel.add(wrapper, BorderLayout.CENTER);
        page.add(panel, BorderLayout.CENTER);

        refreshTasks();
        return page;
    }

    private void refreshTasks() {
        JPanel list = findNamedPanel(content, "tasksList");
        if (list == null) return;

        list.removeAll();

        for (Task task : tasks) {
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(12, 5, 12, 5));

            JCheckBox check = new JCheckBox();
            check.setSelected(task.completed);
            check.setOpaque(false);
            check.addActionListener(e -> {
                task.completed = check.isSelected();
                refreshTasks();
                refreshDashboard();
            });

            JLabel name = new JLabel(task.name);
            name.setForeground(WHITE);
            name.setFont(new Font("Segoe UI", Font.PLAIN, 14));

            JLabel info = new JLabel(task.date + "  |  " + task.priority);
            info.setForeground(MUTED);
            info.setFont(new Font("Segoe UI", Font.PLAIN, 11));

            JButton delete = quickButton("Delete", e -> {
                tasks.remove(task);
                refreshTasks();
                refreshDashboard();
                refreshCalendar();
            });

            JPanel labels = new JPanel();
            labels.setOpaque(false);
            labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
            labels.add(name);
            labels.add(Box.createVerticalStrut(5));
            labels.add(info);

            row.add(check, BorderLayout.WEST);
            row.add(labels, BorderLayout.CENTER);
            row.add(delete, BorderLayout.EAST);

            list.add(row);
            list.add(new JSeparator());
        }

        list.revalidate();
        list.repaint();
    }

    private void addTaskDialog() {
        JTextField name = new JTextField();
        JTextField date = new JTextField(selectedDate.toString());
        String[] priorities = {"Low", "Medium", "High"};
        JComboBox<String> priority = new JComboBox<>(priorities);

        JPanel form = new JPanel(new GridLayout(0, 1, 5, 5));
        form.add(new JLabel("Task name:"));
        form.add(name);
        form.add(new JLabel("Date (YYYY-MM-DD):"));
        form.add(date);
        form.add(new JLabel("Priority:"));
        form.add(priority);

        int result = JOptionPane.showConfirmDialog(
                this, form, "Add a new task",
                JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            try {
                if (name.getText().trim().isEmpty()) {
                    throw new IllegalArgumentException("Enter a task name.");
                }

                LocalDate taskDate = LocalDate.parse(date.getText().trim());

                tasks.add(new Task(name.getText().trim(), taskDate,
                        (String) priority.getSelectedItem()));

                refreshTasks();
                refreshDashboard();
                refreshCalendar();

                JOptionPane.showMessageDialog(this, "Task added successfully!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Please enter a valid task name and date in YYYY-MM-DD format.");
            }
        }
    }

    private JPanel createAssistantPage() {
        JPanel page = page("AI Assistant ✧", "Talk to AURA using text or your microphone.");

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(PANEL);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("✦  AURA Chat");
        title.setForeground(WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));

        connectionStatus = new JLabel("Checking connection...");
        connectionStatus.setForeground(MUTED);

        header.add(title, BorderLayout.WEST);
        header.add(connectionStatus, BorderLayout.EAST);

        chatMessages = new JPanel();
        chatMessages.setLayout(new BoxLayout(chatMessages, BoxLayout.Y_AXIS));
        chatMessages.setBackground(PANEL);

        JScrollPane scroll = new JScrollPane(chatMessages);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(PANEL);

        JPanel input = new JPanel(new BorderLayout(8, 0));
        input.setOpaque(false);

        chatInput = new JTextField();
        chatInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        chatInput.setForeground(WHITE);
        chatInput.setCaretColor(WHITE);
        chatInput.setBackground(new Color(43, 52, 77));
        chatInput.setBorder(new EmptyBorder(14, 12, 14, 12));

        JButton voice = quickButton("🎙 Voice", e -> startVoiceInput());
        JButton send = quickButton("Send ➤", e -> sendMessage());

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        controls.setOpaque(false);
        controls.add(voice);
        controls.add(send);

        input.add(chatInput, BorderLayout.CENTER);
        input.add(controls, BorderLayout.EAST);

        chatInput.addActionListener(e -> sendMessage());

        panel.add(header, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(input, BorderLayout.SOUTH);

        page.add(panel, BorderLayout.CENTER);

        addChatBubble("Hello Vaishnavi! 👋 I'm AURA. You can ask me to organise your day, "
                + "add tasks or help with your schedule.", false);

        return page;
    }

    private void addChatBubble(String message, boolean user) {
        if (chatMessages == null) return;

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(7, 5, 7, 5));

        JTextArea text = new JTextArea(message);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        text.setForeground(WHITE);
        text.setBackground(user ? new Color(100, 75, 175)
                : new Color(43, 55, 82));
        text.setBorder(new EmptyBorder(12, 14, 12, 14));
        text.setColumns(35);

        if (user) row.add(text, BorderLayout.EAST);
        else row.add(text, BorderLayout.WEST);

        chatMessages.add(row);
        chatMessages.revalidate();
        chatMessages.repaint();

        SwingUtilities.invokeLater(() -> {
            JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(
                    JScrollPane.class, chatMessages);
            if (scroll != null) {
                scroll.getVerticalScrollBar().setValue(
                        scroll.getVerticalScrollBar().getMaximum());
            }
        });
    }

    private void sendMessage() {
        String message = chatInput.getText().trim();
        if (message.isEmpty()) return;

        addChatBubble(message, true);
        chatInput.setText("");

        addChatBubble("AURA is thinking...", false);

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            protected String doInBackground() throws Exception {
                String json = "{\"message\":\"" + escapeJson(message) + "\"}";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/api/chat"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                json, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = client.send(
                        request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new RuntimeException("Server error " + response.statusCode());
                }

                return extractReply(response.body());
            }

            protected void done() {
                // Remove the temporary thinking bubble.
                if (chatMessages.getComponentCount() > 0) {
                    chatMessages.remove(chatMessages.getComponentCount() - 1);
                }

                try {
                    addChatBubble(get(), false);
                } catch (Exception ex) {
                    addChatBubble("Unable to connect. Please check that the backend "
                            + "is running on port 8080.", false);
                }
            }
        };

        worker.execute();
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String extractReply(String json) {
        for (String key : new String[]{"reply", "message", "response"}) {
            Pattern p = Pattern.compile(
                    "\"" + key + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
                    Pattern.DOTALL);
            Matcher m = p.matcher(json);

            if (m.find()) {
                return m.group(1)
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\");
            }
        }
        return json;
    }

    private void startVoiceInput() {
        addChatBubble("Listening... Speak into your microphone.", false);

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            protected String doInBackground() {
                try {
                    String script =
                            "Add-Type -AssemblyName System.Speech; " +
                            "$r = New-Object System.Speech.Recognition.SpeechRecognitionEngine; " +
                            "$r.SetInputToDefaultAudioDevice(); " +
                            "$r.LoadGrammar((New-Object System.Speech.Recognition.DictationGrammar)); " +
                            "$r.InitialSilenceTimeout = [TimeSpan]::FromSeconds(5); " +
                            "$r.BabbleTimeout = [TimeSpan]::FromSeconds(2); " +
                            "$r.EndSilenceTimeout = [TimeSpan]::FromSeconds(1); " +
                            "$x = $r.Recognize([TimeSpan]::FromSeconds(12)); " +
                            "if ($x -ne $null) { Write-Output $x.Text }";

                    Process process = new ProcessBuilder(
                            "powershell.exe", "-NoProfile", "-Command", script)
                            .redirectErrorStream(true)
                            .start();

                    Scanner scanner = new Scanner(process.getInputStream());
                    StringBuilder result = new StringBuilder();

                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        result.append(line).append(" ");
                    }

                    process.waitFor();
                    return result.toString().trim();

                } catch (Exception ex) {
                    return "";
                }
            }

            protected void done() {
                try {
                    String spoken = get();

                    if (spoken.isEmpty()) {
                        addChatBubble("I couldn't recognise your speech. "
                                + "Please check your microphone and Windows speech support.",
                                false);
                    } else {
                        chatInput.setText(spoken);
                        sendMessage();
                    }
                } catch (Exception ex) {
                    addChatBubble("Voice recognition failed. Please try typing instead.",
                            false);
                }
            }
        };

        worker.execute();
    }

    private JPanel createBriefingPage() {
        JPanel page = page("Daily Briefing ☀", "A quick look at your day.");

        JPanel panel = sectionPanel("Your daily overview");

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        JLabel date = new JLabel("📅  " + LocalDate.now().format(
                DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")));
        date.setForeground(WHITE);
        date.setFont(new Font("Segoe UI", Font.BOLD, 17));

        info.add(date);
        info.add(Box.createVerticalStrut(20));

        int today = 0;
        int completed = 0;

        for (Task task : tasks) {
            if (task.date.equals(LocalDate.now())) today++;
            if (task.completed) completed++;
        }

        JLabel tasksToday = new JLabel("Tasks scheduled for today: " + today);
        tasksToday.setForeground(WHITE);
        tasksToday.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        JLabel completedLabel = new JLabel("Tasks completed: " + completed);
        completedLabel.setForeground(GREEN);
        completedLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        info.add(tasksToday);
        info.add(Box.createVerticalStrut(10));
        info.add(completedLabel);
        info.add(Box.createVerticalStrut(25));

        JLabel upcoming = new JLabel("UPCOMING TASKS");
        upcoming.setForeground(new Color(196, 181, 253));
        upcoming.setFont(new Font("Segoe UI", Font.BOLD, 12));
        info.add(upcoming);
        info.add(Box.createVerticalStrut(12));

        for (Task task : tasks) {
            if (!task.completed) {
                JLabel item = new JLabel("•  " + task.name + "  —  " + task.date);
                item.setForeground(WHITE);
                item.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                info.add(item);
                info.add(Box.createVerticalStrut(10));
            }
        }

        JButton refresh = quickButton("↻ Refresh briefing", e -> {
            cards.show(content, "Briefing");
            JOptionPane.showMessageDialog(this, "Your briefing has been refreshed.");
        });

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.setOpaque(false);
        bottom.add(refresh);

        panel.add(info, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        page.add(panel, BorderLayout.CENTER);

        return page;
    }

    private void refreshDashboard() {
        if (taskCountLabel == null || completedCountLabel == null) return;

        taskCountLabel.setText(String.valueOf(tasks.size()));

        long completed = tasks.stream().filter(t -> t.completed).count();
        completedCountLabel.setText(String.valueOf(completed));
    }

    private void checkBackend() {
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            protected Boolean doInBackground() {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/api/health"))
                            .GET().build();

                    HttpResponse<String> response = client.send(
                            request, HttpResponse.BodyHandlers.ofString());

                    return response.statusCode() >= 200
                            && response.statusCode() < 300;
                } catch (Exception e) {
                    return false;
                }
            }

            protected void done() {
                if (connectionStatus == null) return;
                try {
                    boolean connected = get();
                    connectionStatus.setText(connected
                            ? "● Backend connected"
                            : "● Backend offline");
                    connectionStatus.setForeground(
                            connected ? GREEN : new Color(248, 113, 113));
                } catch (Exception e) {
                    connectionStatus.setText("● Backend offline");
                }
            }
        };

        worker.execute();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(PersonalAssistantChatbot::new);
    }
}

