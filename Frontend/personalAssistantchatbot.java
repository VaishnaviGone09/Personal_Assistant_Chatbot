import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class PersonalAssistantChatbot extends JFrame {

    private final JTextArea chatArea;
    private final JTextField inputField;
    private final JButton sendButton;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public PersonalAssistantChatbot() {
        setTitle("Personal Assistant Chatbot");
        setSize(600, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(new Color(240, 244, 249));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel heading = new JLabel("Personal Assistant Chatbot");
        heading.setFont(new Font("Arial", Font.BOLD, 24));
        heading.setForeground(new Color(35, 65, 110));
        heading.setHorizontalAlignment(SwingConstants.CENTER);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Arial", Font.PLAIN, 16));
        chatArea.setText(
                "Assistant: Hello! Welcome to your Personal Assistant Chatbot.\n" +
                "Assistant: Messages are sent to your backend.\n\n"
        );

        JScrollPane scrollPane = new JScrollPane(chatArea);

        inputField = new JTextField();
        inputField.setFont(new Font("Arial", Font.PLAIN, 16));

        sendButton = new JButton("Send");
        sendButton.setFont(new Font("Arial", Font.BOLD, 15));
        sendButton.setBackground(new Color(35, 100, 180));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);

        JPanel bottomPanel = new JPanel(new BorderLayout(8, 8));
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        mainPanel.add(heading, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel);

        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());
    }

    private void sendMessage() {
        String message = inputField.getText().trim();

        if (message.isEmpty()) {
            return;
        }

        chatArea.append("You: " + message + "\n");
        inputField.setText("");
        inputField.setEnabled(false);
        sendButton.setEnabled(false);
        chatArea.append("Assistant: Thinking...\n");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                String jsonBody = "{\"message\":\"" + escapeJson(message) + "\"}";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/api/chat"))
                        .timeout(Duration.ofSeconds(60))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new RuntimeException(
                            "Backend returned HTTP " + response.statusCode()
                    );
                }

                return extractJsonString(response.body(), "reply");
            }

            @Override
            protected void done() {
                try {
                    String reply = get();
                    chatArea.append(reply + "\n\n");
                } catch (Exception ex) {
                    chatArea.append(
                            "Sorry, I couldn't contact the backend.\n" +
                            "Check that your backend is running at localhost:8080.\n" +
                            "Details: " + ex.getMessage() + "\n\n"
                    );
                } finally {
                    inputField.setEnabled(true);
                    sendButton.setEnabled(true);
                    inputField.requestFocusInWindow();
                }
            }
        }.execute();
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    private static String extractJsonString(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyIndex = json.indexOf(marker);

        if (keyIndex < 0) {
            throw new RuntimeException("No '" + key + "' field in backend response.");
        }

        int colon = json.indexOf(':', keyIndex + marker.length());
        int start = json.indexOf('"', colon + 1);

        if (colon < 0 || start < 0) {
            throw new RuntimeException("Invalid backend response.");
        }

        StringBuilder result = new StringBuilder();
        boolean escaped = false;

        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                switch (c) {
                    case 'n': result.append('\n'); break;
                    case 'r': result.append('\r'); break;
                    case 't': result.append('\t'); break;
                    case '"': result.append('"'); break;
                    case '\\': result.append('\\'); break;
                    case '/': result.append('/'); break;
                    case 'b': result.append('\b'); break;
                    case 'f': result.append('\f'); break;
                    case 'u':
                        if (i + 4 >= json.length()) {
                            throw new RuntimeException("Invalid Unicode escape.");
                        }
                        result.append((char) Integer.parseInt(
                                json.substring(i + 1, i + 5), 16));
                        i += 4;
                        break;
                    default:
                        throw new RuntimeException("Invalid JSON escape.");
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return result.toString();
            } else {
                result.append(c);
            }
        }

        throw new RuntimeException("Incomplete JSON reply.");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new PersonalAssistantChatbot().setVisible(true);
        });
    }
}
