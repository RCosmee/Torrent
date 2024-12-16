package trabalho;


import java.io.IOException;
import java.net.InetAddress;
import java.security.NoSuchAlgorithmException;


import javax.swing.SwingUtilities;

public class MainClient3 {

    public static void main(String[] args) throws IOException {
        //Node A = new Node("NodeA", 5000); //new Node("A", 8081);
        int port = 8083;
        Node nodeC = new Node("NodeC",InetAddress.getByName("localhost"),  port,"pastasParaDiscussao","dl3");

        SwingUtilities.invokeLater(() -> {
            GUI gui = new GUI(nodeC, port);
            gui.setVisible(true);
            try {
                nodeC.saveFiles(nodeC.lerpasta());
            } catch (NoSuchAlgorithmException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });





    }

}