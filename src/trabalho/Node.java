package trabalho;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Node implements Serializable {
	private transient final ExecutorService threadPool = Executors.newFixedThreadPool(5);
	private static final long serialVersionUID = 1L;

	private HashMap<File, String> files;
	private transient HashMap<Integer, NewConnectionRequest> connections; 
	private transient ServerSocket serverSocket; 
	private transient HashMap<NewConnectionRequest, Integer> ConnectionCounterMap;
	private String nodeName;
	private InetAddress adress;
	private int port;
	private transient DownloadTasksManager dtm;
	private String directoryPath;
	private transient Map<String, DownloadTasksManager> dtmMap;

	private long time;

	private transient HashMap<File, String> hmfiles; 

	public Node(String nodeName, InetAddress adress, int port, String path, String dl) throws IOException {
		this.nodeName = nodeName;
		this.adress = adress;
		this.port = port;
		this.directoryPath = System.getProperty("user.dir") + "/" + path + "/" + dl;
		this.connections = new HashMap<>();
		this.files = new HashMap<>();
		ConnectionCounterMap = new HashMap<>();
		Servidor servidor = new Servidor(port, this);
		servidor.start();
	}

	public String getName() {
		return nodeName;
	}

	public int getPort() {
		return port;
	}

	public DownloadTasksManager getDTM(String Hash) {
		return dtmMap.get(Hash);
	}

	public void saveFiles(HashMap<File, String> f) {
		this.files = f;
		for (Map.Entry<File, String> entry : f.entrySet()) {
			String hash = entry.getValue();
			File file = entry.getKey();
			System.out.println("Hash: " + hash + ", File: " + file.getName());
		}
	}

	public void connectToPeer(String host, int gport, boolean connectback) {
		if (this.port == gport)
			return;
		NewConnectionRequest client = new NewConnectionRequest(host, gport, connectback, this);
		client.start();
		connections.put(gport, client);
	}

	public List<FileSearchResult> searchFileByKeyword(String keyword) {
		WordSearchMessage searchMessage = new WordSearchMessage(keyword);
		List<FileSearchResult> resultados = new ArrayList<>();

		for (Map.Entry<Integer, NewConnectionRequest> entry : connections.entrySet()) {
			List<FileSearchResult> resultadosRemotos = entry.getValue().getList(searchMessage);
			resultados.addAll(resultadosRemotos);
		}

		return resultados;
	}

	
	/*public int download(FileSearchResult fsr, Map<String, Integer> fornecedores) {
	    try {
	        time = System.currentTimeMillis();
	        ConnectionCounterMap.clear();
	        this.dtm = new DownloadTasksManager(fsr); 
	        dtm.waitForCompletion(); 

	        
	        int finalTime = (int) ((System.currentTimeMillis() - time) / 1000); // Tempo em segundos

	        for (Map.Entry<NewConnectionRequest, Integer> entry : ConnectionCounterMap.entrySet()) {
	            String supplierInfo = "endereco=" + entry.getKey().getAdress().toString() + ", porto="
	                    + entry.getKey().getServerPort();
	            fornecedores.put(supplierInfo, entry.getValue());
	        }

	        return finalTime;
	    } catch (InterruptedException | IOException e) {
	        Thread.currentThread().interrupt();
	        throw new RuntimeException("Download interrupted", e);
	    }
	}*/
	public int download(List<FileSearchResult> fsrList, Map<String, Integer> fornecedores) {
	    dtmMap = new HashMap<>();
	    List<Thread> threads = new ArrayList<>();

	    try {
	        time = System.currentTimeMillis(); 
	        ConnectionCounterMap.clear(); 

	        //Por cada FileSearchResult presente na lista criamos uma thread Downloadtasksmanager
	        for (FileSearchResult fsr : fsrList) {
	            DownloadTasksManager dtm = new DownloadTasksManager(fsr);
	            dtmMap.put(fsr.getHashCode(), dtm); 

	            Thread thread = new Thread(() -> {
	                try {
	                    dtm.waitForCompletion(); 
	                } catch (InterruptedException | IOException e) {
	                    Thread.currentThread().interrupt();
	                    System.err.println("Download interrupted for: " + fsr.getName());
	                }
	            });

	            threads.add(thread); 
	            thread.start(); 
	        }

	        // Espera que todas as threads terminem
	        for (Thread thread : threads) {
	            thread.join(); 
	        }

	        //Todos os endereços e portos dos elementos de NewConnectionRequest e o seu Count são colocados na lista "fornecedores" da GUI
	        synchronized (ConnectionCounterMap) {
	            for (Map.Entry<NewConnectionRequest, Integer> entry : ConnectionCounterMap.entrySet()) {
	                String supplierInfo = "endereco=" + entry.getKey().getAdress().toString() + ", porto="
	                        + entry.getKey().getServerPort();
	                fornecedores.put(supplierInfo, entry.getValue());
	            }
	        }

	        int finalTime = (int) ((System.currentTimeMillis() - time) / 1000); // Tempo em segundos
	        return finalTime;

	    } catch (InterruptedException e) {
	        Thread.currentThread().interrupt();
	        throw new RuntimeException("Download interrupted", e);
	    }
	}


	public List<NewConnectionRequest> searchPeerByFileName(String keyword) {
		WordSearchMessage searchMessage = new WordSearchMessage(keyword);
		List<Integer> resultados = new ArrayList<>();
		List<NewConnectionRequest> result = new ArrayList<>();

		for (Map.Entry<Integer, NewConnectionRequest> entry : connections.entrySet()) {
			List<FileSearchResult> resultadosRemotos = entry.getValue().getList(searchMessage);
			if (resultadosRemotos != null && !resultadosRemotos.isEmpty()) { 
				resultados.add(Integer.valueOf(resultadosRemotos.get(0).getPort()));
			}
		}
		for (int i = 0; i < resultados.size(); i++) {
			result.add(connections.get(resultados.get(i).intValue()));
		}
		return result;
	}

	public class Servidor extends Thread {
		private int port;
		private Node no;

		public Servidor(int port, Node no) {
			this.port = port;
			this.no = no;
		}

		public void run() {
			startServer();
		}

		public void startServer() {
			try {
				serverSocket = new ServerSocket(port);
				while (true) {
					waitForConnection();
				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (serverSocket != null) {
					try {
						serverSocket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}

		private void waitForConnection() throws IOException {
			Socket connection = serverSocket.accept();
			ConnectionHandler handler = new ConnectionHandler(connection, no);
			handler.start();
			System.out.println("Connection to " + connection.getLocalPort() + " established!");
		}
	}

	public class NewConnectionRequest extends Thread {
		private Socket connection;
		private ObjectInputStream in;
		private ObjectOutputStream out;

		private String serverHostName;
		private int serverPort;
		private boolean connectbackbool;
		private Node no;
		
		private static final Lock lock = new ReentrantLock();

		public NewConnectionRequest(String serverHostName, int serverPort, boolean connectbackbool, Node no) {
			this.serverHostName = serverHostName;
			this.serverPort = serverPort;
			this.connectbackbool = connectbackbool;
			this.no = no;
		}

		public void run() {
			try {
				connectToServer();
				getStreams();
				processConnection();
			} catch (IOException | ClassNotFoundException e) {
				e.printStackTrace();
			}
		}

		private void connectToServer() throws IOException {
			connection = new Socket(InetAddress.getByName(serverHostName), serverPort);
			System.out.println("Connection to " + serverHostName + " on port " + serverPort + " established!");
		}

		private void getStreams() throws IOException {
			out = new ObjectOutputStream(connection.getOutputStream());
			out.flush();
			in = new ObjectInputStream(connection.getInputStream());
		}

		private void processConnection() throws IOException, ClassNotFoundException {
			if (connectbackbool) {
				out.writeObject("connectback " + no.getPort());
				out.flush();
			}
		}

		public void closeConnection() {
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
                if (connection != null) {
                    connection.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

		public Socket getSocket() {
			return connection;
		}

		public ObjectInputStream getIn() {
			return in;
		}

		public ObjectOutputStream getOut() {
			return out;
		}

		public InetAddress getAdress() {
			return adress;
		}

		public int getServerPort() {
			return serverPort;
		}

		public List<FileSearchResult> getList(WordSearchMessage searchMessage) {
		    try {
		        // Enviar a mensagem ao servidor
		        out.writeObject(searchMessage);
		        out.flush();

		        // Ler a resposta do servidor
		        Object resposta = in.readObject();
		        if (resposta instanceof List) {
		            @SuppressWarnings("unchecked")
		            List<FileSearchResult> r = (List<FileSearchResult>) resposta;
		            return r;
		        } else {
		            System.err.println("Resposta inesperada do servidor: " + resposta.getClass().getName());
		        }
		    }catch (IOException | ClassNotFoundException e) {
		        System.err.println("Erro ao comunicar com o servidor.");
		        e.printStackTrace();
		    }
		    return new ArrayList<>(); // Retorna lista vazia se ocorrer um erro
		}


	    public void sendBlockRequest(FileBlockRequestMessage request) {
	        lock.lock(); 
	        try {
	            out.writeObject(request);
	            out.flush();

	            Object response = in.readObject();
	            if (response instanceof FileBlockAnswerMessage) {
	                FileBlockAnswerMessage answer = (FileBlockAnswerMessage) response;
	                no.getDTM(answer.getHash()).submitResult(answer);
	            } else {
	                System.err.println("Unexpected response type: " + response.getClass().getName());
	            }
	        } catch (IOException | ClassNotFoundException e) {
	            e.printStackTrace();
	        } finally {
	            lock.unlock();
	        }
	    }
	
	}

	public class ConnectionHandler extends Thread {
		private Socket connection;
		private ObjectInputStream in;
		private ObjectOutputStream out;
		private Node no;

		public ConnectionHandler(Socket connection, Node no) {
			this.connection = connection;
			this.no = no;
		}

		@Override
		public void run() {
			try {
				getStreams();
				processConnection();
			} catch (IOException | ClassNotFoundException e) {
				e.printStackTrace();
			} finally {
				closeConnection();
			}
		}

		private void getStreams() throws IOException {
			out = new ObjectOutputStream(connection.getOutputStream());
			out.flush(); 
			in = new ObjectInputStream(connection.getInputStream());
		}

		private void processConnection() throws IOException, ClassNotFoundException {
			while (true) {
				Object obj = in.readObject();
				if (obj instanceof WordSearchMessage) {
					handleSearchRequest((WordSearchMessage) obj);

				} else if (obj instanceof FileBlockRequestMessage) {
					handleFileBlockRequest((FileBlockRequestMessage) obj);

				} else if (obj instanceof String) {
					String msg = (String) obj;
					if (msg.startsWith("connectback")) {
						String[] parts = msg.split(" ");
						no.connectToPeer("localhost", Integer.parseInt(parts[1]), false);
					}
				}
			}
		}

		protected void handleSearchRequest(WordSearchMessage searchMessage) {
			List<FileSearchResult> searchResults = new ArrayList<>();

			for (Map.Entry<File, String> entry : files.entrySet()) {
				File file = entry.getKey();
				if (file.getName().contains(searchMessage.filter)) {
					try {
						FileSearchResult result = new FileSearchResult(file, searchMessage, adress, port);
						searchResults.add(result);
					} catch (NoSuchAlgorithmException | IOException e) {
						e.printStackTrace();
					}
				}
			}

			sendSearchResults(searchResults);
		}

		private void sendSearchResults(List<FileSearchResult> searchResults) {
		    try {
		        out.writeObject(searchResults); // Enviar resultados da pesquisa
		        out.flush(); 
		    } catch (IOException e) {
		        System.err.println("Erro ao enviar os resultados da pesquisa.");
		        e.printStackTrace();
		    }
		}

		public void handleFileBlockRequest(FileBlockRequestMessage request) {
			threadPool.submit(() -> {
				try {
					byte[] blockData = readFileBlock(request);
					FileBlockAnswerMessage answer = new FileBlockAnswerMessage(request.hashCode, request.offset,
							blockData);
					out.writeObject(answer);
					out.flush();
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		}

		private byte[] readFileBlock(FileBlockRequestMessage request) throws IOException {
			File file = findFileByHash(request.hashCode);
			try (FileInputStream fis = new FileInputStream(file)) {
				fis.skip(request.offset);
				byte[] block = new byte[request.length];
				fis.read(block);
				return block;
			}
		}

		private File findFileByHash(String hashCode) {
			for (Map.Entry<File, String> entry : files.entrySet()) {
				if (entry.getValue().equals(hashCode)) {
					return entry.getKey();
				}
			}
			throw new IllegalArgumentException("File not found for hash: " + hashCode);
		}

		private void closeConnection() {
			try {
				if (in != null)
					in.close();
				if (out != null)
					out.close();
				if (connection != null)
					connection.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public HashMap<File, String> lerpasta() throws NoSuchAlgorithmException, IOException {
		hmfiles = new HashMap<>();
		File rootFolder = new File(directoryPath);

		if (rootFolder.exists() && rootFolder.isDirectory()) {
			File[] files = rootFolder.listFiles();
			for (File file : files) {
				System.out.println(file);
				String hash = calculateSHA256Hash(file);
				System.out.println(hash);
				hmfiles.put(file, hash);
			}
		} else {
			System.out.println("O diretório especificado não existe.");
		}
		return hmfiles;
	}

	public String calculateSHA256Hash(File file) throws NoSuchAlgorithmException, IOException {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");

		// Le o arquivo em blocos para calcular o hash de forma mais eficiente
		try (FileInputStream fis = new FileInputStream(file)) {
			byte[] byteBuffer = new byte[1024];
			int bytesRead;

			// Le o arquivo em blocos e atualiza o digest
			while ((bytesRead = fis.read(byteBuffer)) != -1) {
				digest.update(byteBuffer, 0, bytesRead);
			}
		}

		// Converte o hash em bytes para uma string hexadecimal
		byte[] hashBytes = digest.digest();
		StringBuilder hashString = new StringBuilder();

		for (byte b : hashBytes) {
			hashString.append(String.format("%02x", b));
		}

		return hashString.toString();
	}

	public class FileSearchResult implements Serializable {
		private static final long serialVersionUID = 2L;
		WordSearchMessage searchMessage;
		String hashCode;
		long size;
		String name;
		InetAddress address;
		int port;

		public FileSearchResult(File file, WordSearchMessage searchMessage, InetAddress address, int port)
				throws NoSuchAlgorithmException, IOException {
			this.searchMessage = searchMessage;
			this.hashCode = hmfiles.get(file);
			this.size = file.length();
			this.name = file.getName();
			this.address = address;
			this.port = port;
		}

		public WordSearchMessage getSearchMessage() {
			return searchMessage;
		}

		public String getHashCode() {
			return hashCode;
		}

		public long getSize() {
			return size;
		}

		public String getName() {
			return name;
		}

		public InetAddress getAddress() {
			return address;
		}

		public int getPort() {
			return port;
		}
	}

	public static class WordSearchMessage implements Serializable {
		private static final long serialVersionUID = 3L;
		String filter;

		public WordSearchMessage(String filter) {
			this.filter = filter;
		}

		public String getFilter() {
			return filter;
		}
	}

	public class FileBlockRequestMessage implements Serializable {
		private static final long serialVersionUID = 4L;
		public String hashCode;
		public int offset;
		public int length; 

		public FileBlockRequestMessage(String hashCode, int offset, int length) {
			this.hashCode = hashCode;
			this.offset = offset;
			this.length = length;
		}

	}

	public class FileBlockAnswerMessage implements Serializable {
		private static final long serialVersionUID = 5L;
		public String hashCode;
		public int offset;
		public byte[] data;

		public FileBlockAnswerMessage(String hashCode, int offset, byte[] data) {
			this.hashCode = hashCode;
			this.offset = offset;
			this.data = data;
		}
		public String getHash() {
			return hashCode;
		}
	}


	/*public class DownloadTasksManager{
	    private final List<FileBlockRequestMessage> blocksSearch; // Lista de blocos pendentes
	    private final List<FileBlockAnswerMessage> blocksResult; // Lista de blocos recebidos
	    private final FileSearchResult fileSR;
	    private int numBlocks;
	    private int receivedBlocks; // Contador de blocos recebidos
	    private final Lock lock;
	    private final Condition allBlocksReceived;

	    public DownloadTasksManager(FileSearchResult fileSR) {
	        this.blocksSearch = new ArrayList<>();
	        this.fileSR = fileSR;
	        this.blocksResult = new ArrayList<>();
	        this.lock = new ReentrantLock();
	        this.allBlocksReceived = lock.newCondition();
	        this.receivedBlocks = 0;

	        createBlock(fileSR); // Popula blocksSearch
	        new SearcherThread(this, searchPeerByFileName(fileSR.getName())).start();
	    }

	    public FileBlockRequestMessage getChunk() {
	        lock.lock();
	        try {
				if (!blocksSearch.isEmpty()) { // Verificação adicionada
					return blocksSearch.remove(0);
				}
				return null; // Retorna null se não houver mais chunks
	        } finally {
	            lock.unlock();
	        }
	    }

	    public void submitResult(FileBlockAnswerMessage chunk) throws IOException {

	    	   lock.lock();
		        try {
	          	blocksResult.add(chunk);
	            receivedBlocks++;
	            System.out.println("Blocos recebidos: " + receivedBlocks + "/" + numBlocks);

	            // Se todos os blocos foram recebidos, sinaliza para quem está aguardando
	            if (receivedBlocks == numBlocks) {
	                allBlocksReceived.signalAll();
	            }
		        } finally {
		            lock.unlock();
		        }
	    
	    }

	    public void createBlock(FileSearchResult fsr) {
	        int offset = 0;
	        int hop = 10240; // Tamanho do bloco

	        while (offset < fsr.getSize()) {
	            int length = (int) Math.min(hop, fsr.getSize() - offset);
	            blocksSearch.add(new FileBlockRequestMessage(fsr.getHashCode(), offset, length));
	            offset += length;
	            numBlocks++;
	        }

	        System.out.println("Total de blocos criados: " + blocksSearch.size());
	    }

	    public void waitForCompletion() throws InterruptedException, IOException {
	        lock.lock();
	        try {
	            // Espera até que todos os blocos sejam recebidos
	            while (receivedBlocks < numBlocks) {
	                allBlocksReceived.await();
	            }
	            combineBlocksToFile(); // Combina os blocos em um arquivo
	        } finally {
	            lock.unlock();
	        }
	    }

	    public void combineBlocksToFile() throws IOException {
	        blocksResult.sort(Comparator.comparingInt(b -> b.offset)); // Ordena os blocos pelo offset
	        File outputFile = new File(directoryPath, fileSR.getName()); // Caminho do arquivo
	        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
	            for (FileBlockAnswerMessage block : blocksResult) {
	                fos.write(block.data);
	            }
	        }
	        System.out.println("Arquivo completo salvo em: " + outputFile.getAbsolutePath());

	        try {
	            String hash = calculateSHA256Hash(outputFile);
	            System.out.println(hash);
	            hmfiles.put(outputFile, hash);
	        } catch (NoSuchAlgorithmException e) {
	            e.printStackTrace();
	        }
	    }
	}*/
	public class DownloadTasksManager extends Thread{
	    private final List<FileBlockRequestMessage> blocksSearch; 
	    private final List<FileBlockAnswerMessage> blocksResult; 
	    private final FileSearchResult fileSR;
	    private int numBlocks;
	    private transient CountDownLatch latch; 

	    public DownloadTasksManager(FileSearchResult fileSR) {
	        this.blocksSearch = new ArrayList<>();
	        this.fileSR = fileSR;
	        createBlock(fileSR); 
	        this.blocksResult = new ArrayList<>();
	        this.latch = new CountDownLatch(numBlocks); 
	        new SearcherThread(this, searchPeerByFileName(fileSR.getName())).start();
	    }

	    public synchronized FileBlockRequestMessage getChunk() {
	        if (!blocksSearch.isEmpty()) { 
	        	return blocksSearch.remove(0);
	        }
	        return null; // Retorna null se não houver mais chunks
	    }

	    public synchronized void submitResult(FileBlockAnswerMessage chunk) throws IOException {
	        blocksResult.add(chunk);
	        latch.countDown(); // Decrementa o latch por cada bloco recebido
	        System.out.println("Blocos recebidos: " + (numBlocks - latch.getCount()) + "/" + numBlocks);
	    }

	    public void createBlock(FileSearchResult fsr) {
	        int offset = 0;
	        int hop = 10240;

	        while (offset < fsr.getSize()) {
	            int length = (int) Math.min(hop, fsr.getSize() - offset);
	            blocksSearch.add(new FileBlockRequestMessage(fsr.getHashCode(), offset, length));
	            offset += length;
	            numBlocks++;
	        }

	        System.out.println("Total blocks created: " + blocksSearch.size());
	    }

	    public void waitForCompletion() throws InterruptedException, IOException {
	        latch.await(); // Espera ate os blocos serem recebidos
	        combineBlocksToFile(); 
	    }

	    public void combineBlocksToFile() throws IOException {
	        blocksResult.sort(Comparator.comparingInt(b -> b.offset)); 
	        File outputFile = new File(directoryPath, fileSR.getName()); 
	        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
	            for (FileBlockAnswerMessage block : blocksResult) {
	                fos.write(block.data);
	            }
	        }
	        System.out.println("Arquivo completo salvo em: " + outputFile.getAbsolutePath());

	        try {
	            String hash = calculateSHA256Hash(outputFile);
	            System.out.println(hash);
	            hmfiles.put(outputFile, hash);
	        } catch (NoSuchAlgorithmException e) {
	            e.printStackTrace();
	        }
	    }
	}


	class SearcherThread extends Thread {
		private transient DownloadTasksManager dtm;
		private transient List<NewConnectionRequest> listaClientes;

		public SearcherThread(DownloadTasksManager dtm, List<NewConnectionRequest> listaClientes) {
			this.dtm = dtm;
			this.listaClientes = listaClientes;
		}

		
		private ArrayList<Integer> javalis=new ArrayList<>();
		private Lock lock=new ReentrantLock();
		private Condition haEspaco=lock.newCondition();
		private Condition haJavali=lock.newCondition();
		
		public void coloca() throws InterruptedException {
			lock.lock();
			try {
				while(javalis.size()==1)
					haEspaco.await();
				javalis.add(1);
				haJavali.signalAll();
			}finally {lock.unlock();}
		}
		public void retira() throws InterruptedException {
			lock.lock();
			try {
				while(javalis.isEmpty())
					haJavali.await();
				haEspaco.signalAll();
				javalis.remove(0);
			} finally {lock.unlock();}
		}
		
		@Override
		public void run() {
			int currentIndex = 0; // Índice para alternar entre fornecedores
			while (!this.isInterrupted()) {
				FileBlockRequestMessage chunk = dtm.getChunk(); // Obtém o próximo chunk
				if (chunk == null) {
					this.interrupt(); 
					return;
				}
				try {
					coloca();
					NewConnectionRequest cliente = listaClientes.get(currentIndex);
					synchronized (ConnectionCounterMap) {
						ConnectionCounterMap.put(cliente, ConnectionCounterMap.getOrDefault(cliente, 0) + 1);
					}
					
					// Envia a requisição de bloco para o cliente
					cliente.sendBlockRequest(chunk);
					
					// Avança para o próximo cliente na lista 
					currentIndex = (currentIndex + 1) % listaClientes.size();
					retira();
					
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				

			}
		}
		

	}

}
