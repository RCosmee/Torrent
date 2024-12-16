package trabalho;


import java.io.IOException;
import java.net.InetAddress;
import java.security.NoSuchAlgorithmException;


import javax.swing.SwingUtilities;

public class MainClient2 {

    public static void main(String[] args) throws IOException {
        //Node A = new Node("NodeA", 5000); //new Node("A", 8081);
        int port = 8082;
        Node nodeB = new Node("NodeB",InetAddress.getByName("localhost"), port,"pastasParaDiscussao","dl2");

        SwingUtilities.invokeLater(() -> {
            GUI gui = new GUI(nodeB, port);
            gui.setVisible(true);
            try {
                nodeB.saveFiles(nodeB.lerpasta());
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