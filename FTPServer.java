import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
 * FTP Server Class
 * 
 * Accepts incoming FTP control connections 
 * with multiple clients
 */
public class FTPServer {
	//Constant values
	private static final int TIMEOUT = 60000;
	private static final int CONTROL_SERVER_PORT = 2151;
	
	public static void main(String[] args) {
		try {
			VirtualDirectory rootDirectory = new VirtualDirectory();
			int maxThreads = Integer.parseInt(args[0]);
			ServerSocket serverSocket = new ServerSocket(CONTROL_SERVER_PORT);
			serverSocket.setSoTimeout(TIMEOUT);
			ExecutorService threadPool = Executors.newFixedThreadPool(maxThreads);
			
			try {
				while(true) {
					Socket clientSocket = serverSocket.accept();
					clientSocket.setSoTimeout(TIMEOUT);
					clientSocket.setTcpNoDelay(true);
					FTPServerThread serverThread = new FTPServerThread(clientSocket, rootDirectory);
					threadPool.execute(serverThread); // if a thread is available in the thread pool, 
												      // assign to this thread the work of serverThread
				}
			}finally {
				threadPool.shutdown();
				serverSocket.close();
			}
		}
		catch(IllegalArgumentException e) {
			System.err.println("FTP Server Died: Invalid Command Line Argument; "
							   + "The server needs to know the maximum number of threads");
		}catch(SocketTimeoutException e) {
			System.err.println("FTP Server Died: Time Out");
			
		}catch (UnknownHostException e) {
			System.err.println("FTP Server Died: Could Not Find Host Local IP Address");
			
		}catch(Exception e) {
			System.err.println("FTP Server Died:\n" + e);
		}
	}

}
