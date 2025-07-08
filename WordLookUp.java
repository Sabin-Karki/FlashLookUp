import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JWindow;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;
public class WordLookUp extends JFrame{ 

    public static void main(String[] args)  {
       
      Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

      Thread monitorClipboard = new Thread(()->{
        String lastText = "";//get last text so it wont keep checking same word again and again
        while(true){
        try{
            String currentText =  (String) clipboard.getData(DataFlavor.stringFlavor);//this makes sure to check for text/string only from the system clipboard;
            System.out.println("new word copied" + currentText);
            //check something new being copied
            if(currentText!=null && !currentText.equals(lastText) ){
                String definition = getDefiniton(currentText); 
                if(definition!=null && !definition.isEmpty()){
                    showPopUp(currentText, definition);
                }
                lastText=currentText;
            }
            Thread.sleep(400);
        }catch(Exception e){
          System.out.println("Clipboard error: " + e.getMessage());
        }
        }
      });
        monitorClipboard.start();
       
       
    }

    public static  String getDefiniton(String word){
        //this method is for implementing logic to fetch meaning of the word
        //using api
        //first open connection
        try{
        URL url =  new URL("https://api.dictionaryapi.dev/api/v2/entries/en/" + word);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        // have to read response now

        BufferedReader reader =  new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String line;
        StringBuilder response = new StringBuilder();
         
        while((line=reader.readLine())!=null){
            response.append(line);
        }
        reader.close();
        return response.toString();

    }catch(Exception e){
        System.out.println("Defintion not found" + e.getMessage());
        return "Defintion not found";
    }
}
public static void showPopUp(String word,String definition){
    JWindow popup = new JWindow();
    popup.setLayout(new BorderLayout());
    JLabel label = new JLabel("<html><b>" + word + "</b><br><br>" + definition + "</html>");
    label.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    label.setBackground(Color.WHITE);
    label.setOpaque(true);

    popup.setSize(320,200);
    popup.add(label,BorderLayout.CENTER);
    popup.pack();

      Point mousePos = MouseInfo.getPointerInfo().getLocation();
    popup.setLocation(mousePos.x - 150, mousePos.y - 100);

    // Show popup
    popup.setVisible(true);

    // Auto-hide after 5 seconds
    new Timer().schedule(new TimerTask() {
        @Override
        public void run() {
            popup.dispose();
        }
    }, 5000);
}

}

