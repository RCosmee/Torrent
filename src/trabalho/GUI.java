package trabalho;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import trabalho.Node.FileSearchResult;

public class GUI extends JFrame {

	private static final long serialVersionUID = 1L;
	private JTextField campoProcura;
	private JButton botaoProcurar;
	private JButton botaoDescarregar;
	private JButton botaoConectar;
	private DefaultListModel<String> Lista;
	private JList<String> listaResultados;
	private List<FileSearchResult>  resultados;
	private Map<String, FileSearchResult> resultMap;
	private Node node;
	private int port;

	public GUI(Node node, int port) {
		this.node = node;
		this.port = port;
		frame();
		componentes();
		addFrameContent();
		actions();
	}

	private void frame() {
		setTitle("IscTorrent " + port);
		setSize(800, 400);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLayout(new BorderLayout());
		setLocationRelativeTo(null);
	}

	private void componentes() {
		campoProcura = new JTextField(20);
		botaoProcurar = new JButton("Procurar");
		Lista = new DefaultListModel<>();
		listaResultados = new JList<>(Lista);
		botaoDescarregar = new JButton("Descarregar");
		botaoConectar = new JButton("Ligar a Nó");
		resultMap = new HashMap<>(); // Inicializa o mapa
		listaResultados.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
	}

	private void addFrameContent() {
		JPanel Top = new JPanel(new BorderLayout());
		Top.add(new JLabel("Texto a procurar: "), BorderLayout.WEST);
		Top.add(campoProcura, BorderLayout.CENTER);
		Top.add(botaoProcurar, BorderLayout.EAST);

		JScrollPane Results = new JScrollPane(listaResultados);

		JPanel Right = new JPanel(new GridLayout(2, 1));
		Right.add(botaoDescarregar);
		Right.add(botaoConectar);

		add(Top, BorderLayout.NORTH);
		add(Results, BorderLayout.CENTER);
		add(Right, BorderLayout.EAST);
	}

	private void actions() {
		botaoProcurar.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String filter = campoProcura.getText();
				if (filter.isEmpty()) {
					JOptionPane.showMessageDialog(GUI.this, "Por favor, insira um texto para procurar.", "Aviso",
							JOptionPane.WARNING_MESSAGE);
					return;
				}

				// Inicia a busca no nó
				resultados = node.searchFileByKeyword(filter);
				if (resultados != null && !resultados.isEmpty()) {
					updateSearchResults(resultados);
				} else {
					JOptionPane.showMessageDialog(GUI.this, "Nenhum resultado encontrado para: " + filter, "Resultados",
							JOptionPane.INFORMATION_MESSAGE);
				}
			}
		});
		
	    botaoDescarregar.addActionListener(new ActionListener() {
	        @Override
	        public void actionPerformed(ActionEvent e) {
	            // Get all selected indices
	            int[] selectedIndices = listaResultados.getSelectedIndices();
	            if (selectedIndices.length == 0) {
	                JOptionPane.showMessageDialog(GUI.this, "Por favor, selecione um ou mais arquivos da lista para descarregar.",
	                        "Aviso", JOptionPane.WARNING_MESSAGE);
	                return;
	            }

	   
	            List<FileSearchResult> selectedResults = new ArrayList<>();
	            for (int index : selectedIndices) {
	                String selectedItem = Lista.getElementAt(index);
	                FileSearchResult selectedResult = resultMap.get(selectedItem);
	                selectedResults.add(selectedResult);
	            }

	          
	            Map<String, Integer> fornecedores = new HashMap<>();
	            try {
	            	int tempoTotal = node.download(selectedResults, fornecedores);
	            	exibirResultadoDescarregamento(fornecedores, tempoTotal);
	            } catch (Exception ex) {
	                JOptionPane.showMessageDialog(GUI.this, "Erro ao iniciar o descarregamento: " + ex.getMessage(),
	                        "Erro", JOptionPane.ERROR_MESSAGE);
	            }
	        }
	    });
	

		botaoConectar.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				connect();
			}
		});
	}

	void updateSearchResults(List<FileSearchResult> results) {
	    Lista.clear(); 
	    resultMap.clear(); 
	    
	    
	    Map<String, List<FileSearchResult>> fileCounts = new HashMap<>();
	    
	    
	    for (FileSearchResult result : results) {
	        String fileName = result.getName();
	        fileCounts.computeIfAbsent(fileName, k -> new ArrayList<>()).add(result);
	    }
	    
	    
	    for (Map.Entry<String, List<FileSearchResult>> entry : fileCounts.entrySet()) {
	        String fileName = entry.getKey();
	        int count = entry.getValue().size();
	        FileSearchResult representativeResult = entry.getValue().get(0); 

	        
	        String item = fileName + " <" + count + ">";
	        Lista.addElement(item); 
	        resultMap.put(item, representativeResult); 
	    }
	}
	
	private void connect() {
		JTextField campoEndereco = new JTextField(10);
	    campoEndereco.setText("localhost");
		JTextField campoPorta = new JTextField(5);
		campoPorta.setText("808");
		
		JPanel frame = new JPanel();
		frame.add(new JLabel("Endereço:"));
		frame.add(campoEndereco);
		frame.add(new JLabel("Porta:"));
		frame.add(campoPorta);

		int resultado = JOptionPane.showConfirmDialog(this, frame, "Conectar a Nó", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);

		if (resultado == JOptionPane.OK_OPTION) {
			String endereco = campoEndereco.getText();
			String porta = campoPorta.getText();
			int portaint = Integer.parseInt(porta);
			node.connectToPeer(endereco, portaint, true);
		} else {
			JOptionPane.showMessageDialog(this, "Conexão abortada pelo user.");
		}
	}

	private void exibirResultadoDescarregamento(Map<String, Integer> fornecedores, int tempoTotal) {
	    StringBuilder mensagem = new StringBuilder("Descarga completa.\n");

	    
	    for (Map.Entry<String, Integer> entrada : fornecedores.entrySet()) {
	        mensagem.append("Fornecedor [").append(entrada.getKey()).append("]:").append(entrada.getValue()).append("\n");
	    }

	    
	    mensagem.append("Tempo decorrido:").append(tempoTotal).append("s");

	    
	    JOptionPane.showMessageDialog(this, mensagem.toString(), "Resultado do Descarregamento", JOptionPane.INFORMATION_MESSAGE);
	}

}