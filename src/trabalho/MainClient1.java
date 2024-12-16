package trabalho;

import java.io.IOException;
import java.net.InetAddress;
import java.security.NoSuchAlgorithmException;

import javax.swing.SwingUtilities;

public class MainClient1 {

    public static void main(String[] args) throws IOException {
        //Node A = new Node("NodeA", 5000); //new Node("A", 8081);
        int port = 8081;
        Node nodeA = new Node("NodeA",InetAddress.getByName("localhost"),  port,"pastasParaDiscussao","dl1");

        SwingUtilities.invokeLater(() -> {
            GUI gui = new GUI(nodeA, port);
            gui.setVisible(true);
            try {
                nodeA.saveFiles(nodeA.lerpasta());
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