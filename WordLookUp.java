import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WordLookUp extends JFrame {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static JWindow currentPopup = null;
    
    public static void main(String[] args) {
        System.out.println("WordLookUp started. Copy a single word to see definition popup.");
        System.out.println("Press Ctrl+C to exit.");
        
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        
        Thread monitorClipboard = new Thread(() -> {
            String lastText = ""; //get last text so it wont keep checking same word again and again
            while (true) {
                try {
                    String currentText = (String) clipboard.getData(DataFlavor.stringFlavor); //this makes sure to check for text/string only from the system clipboard;
                    
                    //check something new being copied
                    if (currentText != null && !currentText.equals(lastText) && currentText.trim().length() > 0) {
                        String cleanedText = currentText.trim().replaceAll("[^a-zA-Z\\s]", "").toLowerCase();
                        if (cleanedText.length() > 0 && cleanedText.split("\\s+").length == 1) {
                            String definition = getDefinition(cleanedText);
                            scheduler.execute(() -> {
                                String meaningOnly = definition.split("<br><br>")[0];
                                System.out.println("Looking up for word : " + cleanedText + "->" + " " + meaningOnly);
                                if (definition != null && !definition.isEmpty()) {
                                    SwingUtilities.invokeLater(() -> showPopUp(cleanedText, definition));
                                }
                            });
                        } else {
                            System.out.println("Skipped (not a single word): " + currentText);
                        }
                        lastText = currentText;
                    }
                    Thread.sleep(500);
                } catch (Exception e) {
                    System.out.println("Clipboard error: " + e.getMessage());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
        
        monitorClipboard.start();
        
        // Keep main thread alive
        try {
            monitorClipboard.join();
        } catch (InterruptedException e) {
            System.out.println("Program interrupted.");
        }
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }));
    }
    
    public static String getDefinition(String word) {
        //this method is for implementing logic to fetch meaning of the word
        //using api
        //first open connection
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            String encodedWord = URLEncoder.encode(word.toLowerCase(), "UTF-8");
            URL url = new URL("https://api.dictionaryapi.dev/api/v2/entries/en/" + encodedWord);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "WordLookUp/1.0");
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                return "Word not found.";
            }
            
            // have to read response now
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuilder response = new StringBuilder();
            
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            String jsonResponse = response.toString();
            if (jsonResponse.contains("title") && jsonResponse.contains("Not Found")) {
                return "Word not found.";
            }
            
            //parsing logic for accurate format
            String definition = parseDefinition(jsonResponse);
            String example = parseExample(jsonResponse);
            
            if (definition != null) {
                StringBuilder result = new StringBuilder(definition);
                if (example != null && !example.isEmpty()) {
                    result.append("<br><br><i>Example: ").append(example).append("</i>");
                }
                return result.toString();
            } else {
                return "Definition not found";
            }
            
        } catch (Exception e) {
            System.out.println("Definition not found" + e.getMessage());
            return "Definition not found";
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    // ignore
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    private static String parseDefinition(String jsonResponse) {
        int defStart = jsonResponse.indexOf("\"definition\":\"");
        if (defStart == -1) return null;
        
        defStart += 14;
        int defEnd = jsonResponse.indexOf("\"", defStart);
        if (defEnd == -1) return null;
        
        String definition = jsonResponse.substring(defStart, defEnd);
        definition = definition.replace("\\n", " ").replace("\\", "");
        return definition.length() > 200 ? definition.substring(0, 200) + "..." : definition;
    }
    
    private static String parseExample(String jsonResponse) {
        int exampleStart = jsonResponse.indexOf("\"example\":\"");
        if (exampleStart == -1) return null;
        
        exampleStart += 11;
        int exampleEnd = jsonResponse.indexOf("\"", exampleStart);
        if (exampleEnd == -1) return null;
        
        String example = jsonResponse.substring(exampleStart, exampleEnd);
        example = example.replace("\\n", " ").replace("\\", "");
        return example.length() > 150 ? example.substring(0, 150) + "..." : example;
    }
    
    public static void showPopUp(String word, String definition) {
        if (currentPopup != null) {
            currentPopup.dispose();
        }
        
        currentPopup = new JWindow();
        currentPopup.setLayout(new BorderLayout());
        
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(new Color(248, 249, 250));
        contentPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
            BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));
        
        JLabel wordLabel = new JLabel(word.toUpperCase());
        wordLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        wordLabel.setForeground(new Color(51, 51, 51));
        
        JLabel definitionLabel = new JLabel("<html>" + definition + "</html>");
        definitionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        definitionLabel.setForeground(new Color(85, 85, 85));
        
        contentPanel.add(wordLabel, BorderLayout.NORTH);
        contentPanel.add(definitionLabel, BorderLayout.CENTER);
        
        currentPopup.add(contentPanel, BorderLayout.CENTER);
        currentPopup.setSize(350, 200);
        currentPopup.pack();
        
        Point mousePos = MouseInfo.getPointerInfo().getLocation();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        
        int x = Math.max(0, Math.min(mousePos.x - currentPopup.getWidth() / 2, 
                                   screenSize.width - currentPopup.getWidth()));
        int y = Math.max(0, Math.min(mousePos.y - currentPopup.getHeight() - 20, 
                                   screenSize.height - currentPopup.getHeight()));
        
        currentPopup.setLocation(x, y);
        currentPopup.setVisible(true);
        
        // Auto-hide after 5 seconds
        scheduler.schedule(() -> {
            SwingUtilities.invokeLater(() -> {
                if (currentPopup != null) {
                    currentPopup.dispose();
                    currentPopup = null;
                }
            });
        }, 6, TimeUnit.SECONDS);
    }
}