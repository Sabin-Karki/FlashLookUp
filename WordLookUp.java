import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Import JavaZOOM libraries for MP3 support

import javazoom.jl.player.Player;
import javazoom.jl.player.advanced.AdvancedPlayer;

public class WordLookUp extends JFrame {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static JWindow currentPopup = null;
    private static Player currentPlayer = null; // For MP3 playback control
    
    static class WordData {
        String definition;
        String audioUrl;
        
        WordData(String definition, String audioUrl) {
            this.definition = definition;
            this.audioUrl = audioUrl;
        }
    }
    
    public static void main(String[] args) {
        System.out.println("WordLookUp started with MP3 support. Copy a single word to see definition popup.");
        System.out.println("Press Ctrl+C to exit.");
        
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        
        Thread monitorClipboard = new Thread(() -> {
            String lastText = "";
            while (true) {
                try {
                    String currentText = (String) clipboard.getData(DataFlavor.stringFlavor);
                    
                    if (currentText != null && !currentText.equals(lastText) && currentText.trim().length() > 0) {
                        String cleanedText = currentText.trim().replaceAll("[^a-zA-Z\\s]", "").toLowerCase();
                        if (cleanedText.length() > 0 && cleanedText.split("\\s+").length == 1) {
                            WordData wordData = getDefinition(cleanedText);
                            scheduler.execute(() -> {
                                String meaningOnly = wordData.definition.split("<br><br>")[0];
                                System.out.println("Looking up for word : " + cleanedText + "->" + " " + meaningOnly);
                                if (wordData.definition != null && !wordData.definition.isEmpty()) {
                                    SwingUtilities.invokeLater(() -> 
                                        showPopUp(cleanedText, wordData.definition, wordData.audioUrl));
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
        
        try {
            monitorClipboard.join();
        } catch (InterruptedException e) {
            System.out.println("Program interrupted.");
        }
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (currentPlayer != null) {
                currentPlayer.close();
            }
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
    
    public static WordData getDefinition(String word) {
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
                return new WordData("Word not found.", null);
            }
            
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuilder response = new StringBuilder();
            
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            String jsonResponse = response.toString();
            if (jsonResponse.contains("title") && jsonResponse.contains("Not Found")) {
                return new WordData("Word not found.", null);
            }
            
            String definition = parseDefinition(jsonResponse);
            String example = parseExample(jsonResponse);
            String audioUrl = parseAudio(jsonResponse);
            
            System.out.println("Audio URL found: " + (audioUrl != null ? audioUrl : "None"));
            
            if (definition != null) {
                StringBuilder result = new StringBuilder(definition);
                if (example != null && !example.isEmpty()) {
                    result.append("<br><br><i>Example: ").append(example).append("</i>");
                }
                return new WordData(result.toString(), audioUrl);
            } else {
                return new WordData("Definition not found", null);
            }
            
        } catch (Exception e) {
            System.out.println("Definition not found: " + e.getMessage());
            return new WordData("Definition not found", null);
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

    private static String parseAudio(String jsonResponse) {
        Pattern audioPattern = Pattern.compile("\"audio\":\"([^\"]+)\"");
        Matcher matcher = audioPattern.matcher(jsonResponse);
        
        while (matcher.find()) {
            String audioUrl = matcher.group(1);
            audioUrl = audioUrl.replace("\\", "");
            
            if (!audioUrl.isEmpty()) {
                if (!audioUrl.startsWith("http")) {
                    audioUrl = "https:" + audioUrl;
                }
                return audioUrl;
            }
        }
        
        return null;
    }

    //  MP3 streaming support with JavaZOOM <this is done to not use any disk space and stream directly from RAM>
    public static void playPronunciation(String audioUrl) {
        if (audioUrl == null || audioUrl.isEmpty()) {
            System.out.println("No audio URL available");
            return;
        }
        
        System.out.println("Streaming MP3 audio from: " + audioUrl);
        
        // Stop any currently playing audio
        if (currentPlayer != null) {
            currentPlayer.close();
            currentPlayer = null;
        }
        
        try {
            URL url = new URL(audioUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "WordLookUp/1.0");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            
            if (connection.getResponseCode() != 200) {
                System.out.println("Failed to access audio: HTTP " + connection.getResponseCode());
                return;
            }
            
            // Check if it's MP3 or try JavaZOOM first
            String contentType = connection.getContentType();
            System.out.println("Audio content type: " + contentType);
            
            InputStream inputStream = connection.getInputStream();
            BufferedInputStream bufferedStream = new BufferedInputStream(inputStream);
            
            // Try JavaZOOM MP3 player first (supports MP3 streaming)
            try {
                currentPlayer = new Player(bufferedStream);
                
                // Play in separate thread to avoid blocking
                new Thread(() -> {
                    try {
                        currentPlayer.play();
                        System.out.println("MP3 audio played successfully (streamed from RAM)");
                    } catch (Exception e) {
                        System.out.println("MP3 playback failed: " + e.getMessage());
                        
                        // Fallback to Java's built-in audio system for WAV files
                        tryBuiltinAudioSystem(audioUrl);
                    } finally {
                        currentPlayer = null;
                        try {
                            bufferedStream.close();
                            inputStream.close();
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }).start();
                
            } catch (Exception e) {
                System.out.println("JavaZOOM MP3 player failed: " + e.getMessage());
                
                // Fallback to built-in audio system
                tryBuiltinAudioSystem(audioUrl);
            }
            
        } catch (Exception e) {
            System.out.println("Could not stream audio: " + e.getMessage());
        }
    }
    
    // Fallback method using Java's built-in audio system (for WAV files)
    private static void tryBuiltinAudioSystem(String audioUrl) {
        try {
            URL url = new URL(audioUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "WordLookUp/1.0");
            
            InputStream inputStream = connection.getInputStream();
            BufferedInputStream bufferedStream = new BufferedInputStream(inputStream);
            
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bufferedStream);
            Clip clip = AudioSystem.getClip();
            clip.open(audioInputStream);
            clip.start();
            
            System.out.println("WAV audio played successfully (built-in system)");
            
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                    try {
                        audioInputStream.close();
                        bufferedStream.close();
                        inputStream.close();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            });
            
        } catch (UnsupportedAudioFileException e) {
            System.out.println("Audio format not supported by built-in system: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Built-in audio system failed: " + e.getMessage());
        }
    }

    private static JLabel createSpeakerIcon() {
        JLabel speakerIcon = new JLabel("🔊");
        speakerIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        speakerIcon.setCursor(new Cursor(Cursor.HAND_CURSOR));
        speakerIcon.setToolTipText("Click to hear pronunciation");
        return speakerIcon;
    }
    
    public static void showPopUp(String word, String definition, String audioUrl) {
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
        
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        headerPanel.setBackground(new Color(248, 249, 250));
        
        JLabel wordLabel = new JLabel(word.toUpperCase());
        wordLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        wordLabel.setForeground(new Color(51, 51, 51));
        
        headerPanel.add(wordLabel);
        
        // Add speaker icon if audio URL is available
        if (audioUrl != null && !audioUrl.isEmpty()) {
            JLabel speakerIcon = createSpeakerIcon();
            speakerIcon.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent evt) {
                    scheduler.execute(() -> playPronunciation(audioUrl));
                }
            });
            headerPanel.add(speakerIcon);
            System.out.println("Speaker icon added for word: " + word);
        } else {
            System.out.println("No audio URL available for word: " + word);
        }
        
        JLabel definitionLabel = new JLabel("<html>" + definition + "</html>");
        definitionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        definitionLabel.setForeground(new Color(85, 85, 85));
        
        contentPanel.add(headerPanel, BorderLayout.NORTH);
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
