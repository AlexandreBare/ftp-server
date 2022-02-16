import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;


/*
 * FTP Server Thread Class
 * Manages the client requests 
 * of each new connection to the FTP Server in parallel
 */
public class FTPServerThread extends Thread {
	
	// Constant values
	private static final int TIMEOUT = 60000;
	private static final int DATA_SERVER_PORT = 2051;
	private static final String ANONYMOUS = "anonymous";
	private static final String USERNAME = "Sam";
	private static final String PASSWORD = "123456";
	
	// Client variables
	private Socket clientSocket;
	private String username;
	private InetAddress ipClient;
	private int portClient;
	private Socket dataClientSocket;
	private OutputStream outputStreamClient;
	
	// Server variables
	private InetAddress ipServer;
	private ServerSocket dataServerSocket;
	
	// Boolean state variables
	private boolean isBinaryTransferType = false;
	private boolean isActiveMode = false;
	private boolean isDataChannelOpen = false;
	private boolean isLoggedIn = false;
	private boolean isAnonymous = true;
	
	// Directory variables
	private VirtualDirectory rootDirectory;
	private VirtualDirectory currentDirectory;
	
	// Miscellaneous
	private String oldPathnameBuffer;
	
	
	/*
	 * Constructor
	 * 
	 * Arguments:
	 * _clientSocket	the client socket
	 * _rootDirectory	the server virtual root directory
	 * 
	 * Throws:
	 * UnknownHostException 	if the host address could not be found 
	 * IOException 				if the client output stream could not be accepted
	 */
	public FTPServerThread(Socket _clientSocket, VirtualDirectory _rootDirectory) throws UnknownHostException, IOException{
		rootDirectory = _rootDirectory;
		currentDirectory = _rootDirectory;
		clientSocket = _clientSocket;
		ipServer = InetAddress.getLocalHost();
		outputStreamClient = clientSocket.getOutputStream();
	}
	
	
	/*
	 * Responds to the client requests
	 * (See RFC959 and further RFC for more information)
	 */
	@Override
	public void run() {
		try {
			sendReply("220 Enter User Name");
			InputStream in = clientSocket.getInputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String request = br.readLine();
			try {
				while(request != null) {
					if (request.length() > 0) {
						handleRequest(request);
					}
					request = br.readLine();
				}
			}finally {
				br.close();
				clientSocket.close();
			}
			
		}catch(SocketTimeoutException e) {
			System.err.println("FTP Server Thread Died: Client Response Times Out");
		}catch(Exception e) {
			System.err.println("FTP Server Thread Died: " + e);
		}
	}
	
	
	/*
	 * Replies to the client request on the control channel
	 * 
	 * Arguments:
	 * reply		the message to send to the client
	 */
	public void sendReply(String reply) {
		if(reply == null)
			return;
		
		try {
			outputStreamClient.write((reply + "\r\n").getBytes());
			outputStreamClient.flush();
		}catch(IOException e) {
			System.err.println("FTP Server Thread: Could Not Send Reply To Client");
		}
	}

	
	/*
	 * Handles client requests
	 * 
	 * Arguments:
	 * request		the FTP request
	 * 
	 * Reply:
	 * - An error message if - there is a syntax error in the arguments ("501")
	 * 						 - the command is not recognized/handled ("500")
	 * - Other messages are handled by intermediate methods
	 */
	public void handleRequest(String request) {
		try {
			// Split in maximum 2 pieces at the 2 first <space> character
			String requestPieces[] = request.split(" ", 2); 
			String command = requestPieces[0];
			String arguments[] = null;
			if(requestPieces.length > 1) { //if the request contains arguments
				arguments = requestPieces[1].split(",");
			}
			
			switch(command) {
				case "USER": requestUSER(arguments[0]);
					break;
				case "PASS": requestPASS(arguments[0]);
					break;
				case "SYST": requestSYST();
					break;
				case "PORT": 
					int[] nums = new int[6];
					for(int i = 0; i < 6; i++) {
						nums[i] = Integer.parseInt(arguments[i]);
					}	
					requestPORT(nums);
					break;
				case "PASV": requestPASV();
					break;
				case "TYPE": 
					if (arguments[0].length() > 1) { 
						sendReply("501 Syntax Error in Arguments; "
								+ "A Unique Character Is Expected "
								+ "- Either 'I' for Binary or 'A' for ASCII Transfer Type");
					}else
						requestTYPE(arguments[0].charAt(0));
					break;
				case "CDUP": requestCDUP();
					break;
				case "CWD": requestCWD(arguments[0]);
					break;
				case "LIST": 
					if(arguments != null)
						requestLIST(arguments[0]);
					else
						requestLIST();
					break;
				case "PWD": requestPWD();
					break;
				case "FEAT": requestFEAT();
					break;
				case "RETR": requestRETR(arguments[0]);
					break;
				case "STOR": requestSTOR(arguments[0]);
					break;
				case "DELE": requestDELE(arguments[0]);
					break;
				case "RNFR": requestRNFR(arguments[0]);
					break;
				case "RNTO": requestRNTO(arguments[0]);
					break;
				case "MDTM": requestMDTM(arguments[0]);
					break;
				default: sendReply("500 Unrecognized Command");
					break;	
			}
			
		}catch(StringIndexOutOfBoundsException | NullPointerException e) {
			sendReply("501 Syntax Error in Arguments");
		}
	}
	
	
	/*
	 * Handles "USER" requests
	 * -> saves the client username
	 * 
	 * Arguments:
	 * _username		the client username
	 * 
	 * Reply:
	 * - An approbation to log in if the user is anonymous ("230")
	 * - Or else, a demand for the password ("331")
	 */
	public void requestUSER(String _username) {
		username = _username;
		if(ANONYMOUS.contentEquals(username)) {
			isLoggedIn = true;
			isAnonymous = true;
			sendReply("230 Authentication Successful");
		}else {
			sendReply("331 Enter the password");
		}
	}
	
	
	/*
	 * Handles "PASS" (PASSword) requests
	 * -> checks the client's password and username validity to log in
	 * 
	 * Arguments:
	 * password		the client password
	 * 
	 * Reply:
	 * - An approbation to log in if the password and the username are correct ("230")
	 * - Or, an error message if - at least one of the two is not correct ("430")
	 * 						     - no username has been sent ("332")
	 */
	public void requestPASS(String password) {
		if(username.contentEquals(""))
			sendReply("332 Need Account For Login");
		
		else if(USERNAME.contentEquals(username) && PASSWORD.contentEquals(password)) {
			isLoggedIn = true;
			isAnonymous = false;
			sendReply("230 Authentication Successful");
			
		}else
			sendReply( "430 Login/Password Incorrect");
	}
	
	
	/*
	 * Handles "SYST" (SYSTem) requests
	 * -> gives the server system
	 * 
	 * Reply:
	 * A message advertising the server system ("215")
	 */
	public void requestSYST() {
		sendReply("215 UNIX Type: L8");
	}
	
	
	/*
	 * Handles "PORT" requests
	 * -> enables active mode for the data connection
	 * -> the client transmits its IP address and selects a port number 
	 * for the server to establish the data connection
	 * 
	 * 
	 * Arguments:
	 * nums		an array containing the 4 numbers of the client IP address 
	 * 			and the 2 numbers (X and Y) to compute the client port number: X * 256 + Y
	 * 
	 * Reply:
	 * - A successful message advertising the entering in active mode ("200")
	 * - Or, an error message if - the client is not logged in ("530")
	 * 						 	 - the number of arguments is not at least of 6 
	 * 							   (only the 6 first are considered) ("501")
	 * 							 - the IP address is not valid ("501")
	 * 							 - the 6 first arguments are not in range [0; 255] ("501")
	 */
	public void requestPORT(int[] nums) {
		if(isLoggedIn == false)
			sendReply("530 Not Logged In");
		
		else if(nums.length >= 6) {
			boolean isUnsignedByte = true;
			for(int i = 0; i < 6; i++) {
				if(nums[i] > 255 || nums[i] < 0) {
					isUnsignedByte = false;
					sendReply("501 Invalid Paramters; 6 Numbers In Range [0; 255] Expected");
				}
			}
			
			if(isUnsignedByte) {
				int i;
				byte[] ipNums = new byte[4];
				for(i = 0; i < 4; i++) {
					ipNums[i] = (byte) nums[i];
				}
				
				try {
					ipClient = InetAddress.getByAddress(ipNums);
					portClient = nums[i] * 256 + nums[i+1];
					isActiveMode = true;
					sendReply("200 Entering Active Mode");
				} catch (UnknownHostException e) {
					sendReply("501 Invalid IP Address");
				}
			}
			
			try {
				establishDataConnection();
			}catch(SocketTimeoutException e) {
				System.err.println("Transfer Aborted; Data Connection Timed Out");
			}catch (IOException e) {
				System.err.println("Can't Open Data Connection: " + e);
			}
		}else
			sendReply("501 Invalid Parameters; 6 Numbers In Range [0; 255] Expected");
	}
	
	
	/*
	 * Handles "PASV" (PASiVe) requests
	 * -> enables passive mode for data connection
	 * -> the server sends its IP address and selects a port number 
	 * to establish the data connection
	 * 
	 * Reply:
	 * - A successful message advertising the entering in passive mode 
	 * associated with the 4 numbers of the server IP address and the 
	 * 2 numbers (X and Y) to compute the client port number: X * 256 + Y ("200")
	 * - Or, an error message if the client is not logged in ("530")
	 */
	public void requestPASV() {
		if(isLoggedIn == false)
			sendReply("530 Not Logged In");
		else {
			isActiveMode = false;
			
			String answer = "227 Entering Passive Mode (";
			byte[] ipNums = ipServer.getAddress();
			for(int i = 0; i < 4; i++) {
				answer += (ipNums[i] & 0xFF) + ",";
			}
			answer += DATA_SERVER_PORT / 256 + "," + DATA_SERVER_PORT % 256 + ")";
			sendReply(answer);
			
			try {
				establishDataConnection();
			}catch(SocketTimeoutException e) {
				System.err.println("Transfer Aborted; Data Connection Timed Out");
			}catch (IOException e) {
				System.err.println("Can't Open Data Connection: " + e);
			}
		}
	}
	
	
	/*
	 * Handles "TYPE" requests
	 * -> selects the file transfer type, 
	 * either 'I' for Binary (images, videos, raw data file, ...) 
	 * or 'A' for ASCII (text files)
	 * 
	 * Arguments:
	 * type		a character corresponding to the transfer type, 
	 * 			'I' for Binary or 'A' for ASCII
	 * 
	 * Reply:
	 * - A successful message advertising the change of transfer type, Binary or ASCII ("200")
	 * - Or, an error message if - the client is not logged in ("530")
	 * 							 - the parameter is invalid: neither 'I', nor 'A' ("504")
	 */
	public void requestTYPE(char type) {
		if(isLoggedIn == false)
			sendReply("530 Not Logged In");
		
		else if(type == 'I') {  // Binary transfer type
			isBinaryTransferType = true;
			sendReply("200 Binary Transfer Type");
			
		}else if (type == 'A') { // ASCII transfer type
			isBinaryTransferType = false;
			sendReply("200 ASCII Transfer Type");
			
		}else
			sendReply("504 Command Not Implemented For the Parameter '" + String.valueOf(type)
			+ "' - Either 'I' (Binary) or 'A' (ASCII) Accepted");
	}
	
	
	/*
	 * Handles "CWD" (Change Working Directory) requests
	 * -> changes the working directory
	 * 
	 * Arguments:
	 * path		the path to the new working directory
	 * 
	 * Reply:
	 * - A successful message advertising the change of working directory ("250")
	 * - Or, an error message if - the client is not logged in ("530")
	 * 							 - the directory can't be found with the path ("550")
	 */
	public void requestCWD(String path) {
		if(isLoggedIn == false)
			sendReply("530 Not Logged In");
		
		else {
			VirtualDirectory buffer = rootDirectory.getDirectory(path, !isAnonymous);
			if(buffer != null) {
				currentDirectory = buffer;
				sendReply("250 Move To Directory " + path);
				
			}else
				sendReply("550 Directory Not Found");
		}
	}
	
	
	/*
	 * Handles "CDUP" (Change Directory UP) requests
	 * -> changes the working directory to the parent directory
	 * 
	 * Reply:
	 * - A successful message advertising the change of working directory ("200")
	 * - Or, an error message if - the client is not logged in ("530")
	 * 							 - there is no parent directory ("550")
	 */
	public void requestCDUP() {
		if(isLoggedIn == false)
			sendReply("530 Not Logged In");
		
		else {
			VirtualDirectory buffer = currentDirectory.getParentDirectory();
			if(buffer != null) {
				currentDirectory = buffer;
				sendReply("200 Move To " + currentDirectory.getDirectoryPath());
				
			}else
				sendReply("550 No Parent Directory");
		}
	}
	
	
	/*
	 * Handles "LIST" requests with an argument
	 * -> lists the content of the given directory, 
	 * the transmission of information is done through the data connection
	 *
	 * Arguments:
	 * pathname		the pathname to the given directory
	 * 
	 * Reply:
	 * - A successful message advertising that - the directory has been found and that the 
	 * 										     server is about to open the data connection ("150")
	 * 										   - the data was correctly transfered ("226")
	 * - Or, an error message if - the client is not logged in ("530")
	 * 						     - the directory can't be found with the path ("451")
	 * 							 - the data connection can't be opened ("425")
	 * 							 - the transfer is aborted because of an error 
	 * 							   when writing in the data channel	("426")	
	 * 							 - the transfer is aborted due to either the server 
	 * 							   or the client data socket that has timed out ("426")
	 */
	public void requestLIST(String pathname) {
		if(isLoggedIn == false)
			sendReply("530 Not Logged In");
		
		else {
			VirtualDirectory directory = rootDirectory.getDirectory(pathname, !isAnonymous);
			if(directory != null) {
				sendReply("150 Directory Found; About To Open Data Connection");
				sendOnDataChannel(directory.printDirectoryContent(!isAnonymous));
			
			}else
				sendReply("451 Path Error; Can't Found the Directory");
		}
	}
	
	
	/*
	 * Handles "LIST" requests without argument
	 * -> lists the content of the current directory, 
	 * the transmission of information is done through the data connection
	 *
	 * Reply:
	 * - A successful message advertising that - the server is about to open the data connection ("150")
	 * 										   - the data was correctly transfered ("226")
	 * - Or, an error message if - the client is not logged in ("530")
	 * 							 - the data connection can't be opened ("425")
	 * 							 - the transfer is aborted because of an error 
	 * 							   when writing in the data channel	("426")	
	 * 							 - the transfer is aborted due to either the server 
	 * 							   or the client data socket that has timed out ("426")							 
	 */
	public void requestLIST() {
		if(isLoggedIn == false)
			sendReply("530 Not Logged In");
		
		else {
			sendReply("150 About To Open Data Connection");
			sendOnDataChannel(currentDirectory.printDirectoryContent(!isAnonymous));
		}
	}
	
	
	/*
	 * Handles "PWD" (Print Working Directory) requests
	 * -> gives the path of the current directory
	 *
	 * Reply:
	 * - A successful message advertising the path of the current directory ("257")
	 * - Or, an error message if there is no current directory loaded (yet) ("550")
	 */
	public void requestPWD() {
		if(currentDirectory == null)
			sendReply("550 Directory Unavailable");
		
		else
			sendReply("257 \"" + currentDirectory.getDirectoryPath() + "\"");
	}
	
	
	/*
	 * Handles "FEAT" (FEATure) requests
	 * -> Lists the additional implemented features that go beyond those defined in RFC959
	 * 
	 * Reply:
	 * A multi-line reply where on each new line, is specified 
	 * an additional feature implemented in the server ("211")
	 */
	public void requestFEAT() {
		
		sendReply("211-Extension Supported by FTP Server: \r\n"
				+ " MDTM\r\n"  // Very important -> add a <space> character before each feature
				+ "211 END\r\n");
	}
	
	
	/*
	 * Handles "RETR" (RETRieve) requests
	 * -> downloads a file from the current directory of the server
	 * 
	 * Arguments:
	 * filename		the filename of the file to download
	 * 
	 * Reply:
	 * - A successful message advertising that - the file has been found and that the server
	 * 								  			 is about to open the data connection ("150")
	 * 										   - the data was correctly transfered ("226")
	 * - Or, an error message if - the client is not logged in ("530")
	 * 						 	 - the file can't be found ("550")
	 * 							 - the data connection can't be opened ("425")
	 * 							 - the transfer is aborted because of an error 
	 * 							   when writing in the data channel	("426")	
	 * 							 - the transfer is aborted due to either the server 
	 * 							   or the client data socket that has timed out ("426")
	 */
	public void requestRETR(String filename) { 
		if(isLoggedIn == false)
			sendReply("530 Not Logged In");
		
		else {
			VirtualFile<?> file = currentDirectory.downloadFile(filename);
			if(file == null)
				sendReply("550 File Can't Be Found");
			
			else {
				sendReply("150 File Found; About To Open Data Connection");
				sendOnDataChannel(file);
			}
		}
	}
	
	
	/*
	 * Handles "STOR" (STORe) requests
	 * -> uploads a file to the server
	 * 
	 * Arguments:
	 * filename		the relative filename of the file to upload
	 * 
	 * Reply:
	 * - A successful message advertising that - the filename is valid and that the server
	 * 								  			 is about to open the data connection ("150")
	 * 										   - the data was correctly uploaded ("226")
	 * - Or, an error message if - the client is not logged in ("530")
	 * 							 - the filename is not allowed ("553")
	 * 							 - the data connection can't be opened ("425")
	 * 							 - the transfer is aborted because of an error 
	 * 							   when reading from the data channel ("426")	
	 * 							 - the transfer is aborted due to either the server 
	 * 							   or the client data socket that has timed out ("426")
	 * 							 - the data to receive has exceeded the maximum transfer size ("452")
	 * 							 - there was a processing error in the uploading of virtual files ("451")
	 */
	public void requestSTOR(String filename) {
		if(isLoggedIn == false)
			sendReply("530 Not Logged In");
		
		else {
			sendReply("150 File Status Okay; About To Open Data Connection");
			VirtualFile<?> virtualFile = null;
			try {
				if(isBinaryTransferType) {
					byte[] data = (byte[])receiveFromDataChannel();
					virtualFile = new VirtualFile<byte[]>(filename, data, data.length);
				}else {
					String data = (String)receiveFromDataChannel();
					virtualFile = new VirtualFile<String>(filename, data, data.length());
				}
				
				if(currentDirectory.uploadFile(virtualFile))
					sendReply("226 Data Uploaded");
				else
					sendReply("451 Processing Error; Upload Failed");
			}catch(InvalidStringFormatException e) {
				sendReply("553 Filename Not Allowed");
			}catch(TransferSizeExceededException e) {
				sendReply("452 Upload aborted; Maximum File Size Exceeded");
			}
			
		}
	}
	
	
	/*
	 * Handles "DELE" (DELEte) requests
	 * -> deletes a specific file from the server
	 * 
	 * Arguments:
	 * filename		the name of the file to delete
	 * 
	 * Reply: 
	 * - A successful message advertising that the file is deleted ("250")
	 * - Or, an error message if - the client is not logged in ("530")
	 * 							 - the file can't be found ("550")
	 */
	public void requestDELE(String filename) {
		if(isLoggedIn == false)
			sendReply("530 Not Logged In");
		
		else if(currentDirectory.removeFile(filename) != null)
			sendReply("250 File " + "\"" + filename + "\"" + " Deleted");
		
		else
			sendReply("550 File Can't Be Found");
	}
	
	
	/*
	 * Handles "RNFR" (REname FRom) requests
	 * -> seeks the file that must be renamed by the request "RNTO"
	 * 
	 * Arguments:
	 * oldFilename		the old name of the file that must be renamed
	 * 
	 * Reply:
	 * - A successful message advertising that the file has been found and 
	 * that the server is waiting for further information to rename the file ("350")
	 * - Or, an error message if - the client is not logged in ("530")
	 * 							 - the file can't be found ("550")
	 */
	public void requestRNFR(String oldFilename) {
		if(isLoggedIn == false)
			sendReply("530 Not Logged In");
		
		else {
			if(currentDirectory.downloadFile(oldFilename) != null) {
				oldPathnameBuffer = oldFilename;
				sendReply("350 File Found; Waiting To Rename");
			
			}else
				sendReply("550 File Can't Be Found");
		}
	}
	
	
	/*
	 * Handles "RNTO" (REname TO) requests
	 * -> renames the file sought by the request "RNFR"
	 * 
	 * Arguments:
	 * newFilename		the new name of the file that must be renamed
	 * 
	 * Reply: 
	 * - A successful message advertising that the file is renamed ("250")
	 * - Or, an error message if - the client is not logged in ("530")
	 * 							 - the new pathname is not allowed ("553")
	 * 							 - an "RNFR" request has not been done previously ("503")
	 */
	public void requestRNTO(String newPathname) {
		if(isLoggedIn == false)
			sendReply("530 Not Logged In");
			
		else if(oldPathnameBuffer == null)
			sendReply("503 Bad Sequence; A Valid \"RNFR\" Request Must Be Done First");
		
		else {
			if(oldPathnameBuffer.contentEquals(newPathname)) // if no change in pathname
				sendReply("250 File " + "\"" + oldPathnameBuffer + "\"" + " Renamed \"" + newPathname + "\"");
						
			else if(currentDirectory.renameFile(oldPathnameBuffer, newPathname)) // if the buffered file has successfully been renamed
				sendReply("250 File " + "\"" + oldPathnameBuffer + "\"" + " Renamed \"" + newPathname + "\"");
			
			else
				sendReply("553 New Pathname \"" + newPathname + "\" Not Allowed");	
		}
		
		oldPathnameBuffer = null;
	}
	
	
	/*
	 * Handles "MDTM" (MoDification TiMe) requests
	 * -> gives the modification time of a file, i.e. the time it was last last modified
	 * 
	 * Arguments:
	 * filename		the filename of the file
	 * 
	 * Reply:
	 * - A successful message advertising the modification time of 
	 * the file in milliseconds since 1st January 1970 ("213")
	 * - Or, an error message if the file can't be found ("550")
	 */
	public void requestMDTM(String filename) {
		if(filename != null) {
			VirtualFile<?> file = currentDirectory.downloadFile(filename);
			if(file != null)
				sendReply("213 " + file.getModificationTime()); 
		}else
			sendReply("500 File Can't Be Found");
			
	}
	
	
	/*
	 * Handles the transmission of data to the client through the data channel
	 * 
	 * Arguments:
	 * data		the data to send to the client
	 * 
	 * Reply:
	 * - A successful message advertising that the data was correctly transfered ("226")
	 * - Or, an error message if - the data connection can't be opened ("425")
	 * 							 - the transfer is aborted because of an error 
	 * 							   when writing in the data channel	("426")	
	 * 							 - the transfer is aborted due to either the server 
	 * 							   or the client data socket that has timed out ("426")
	 */
	public void sendOnDataChannel(Object data) {
		if(!isDataChannelOpen) {
			try {
				establishDataConnection();
			}catch(SocketTimeoutException e) {
				sendReply("426 Transfer aborted; Data Connection Timed Out");
			}catch (IOException e) {
				sendReply("425 Can't Open Data Connection");
			}
		}
		
		if(isDataChannelOpen) {
			try {
				if(data instanceof VirtualFile<?>)
					transferData((VirtualFile<?>) data);
				else
					transferData(data.toString());
				
				sendReply("226 File/Directory Found; Transfer Completed");
			}catch (IOException e) {
				sendReply("426 Transfer aborted");
			}finally {
				try {
					closeDataConnection();
				}catch(IOException e) {
					System.err.println("FTP Server Thread: Could Not Close Data Connection");
				}
			}
		}
	}
	
	
	/*
	 * Handles the transmission of data from the client through the data channel
	 * 
	 * Return:
	 * The data received from the client
	 * 
	 * Reply:
	 * - Nothing if the data was correctly transfered
	 * - Or, an error message if - the data connection can't be opened ("425")
	 * 							 - the transfer is aborted because of an error 
	 * 							   when reading from the data channel ("426")	
	 * 							 - the transfer is aborted due to either the server 
	 * 							   or the client data socket that has timed out ("426")
	 * 							 - the data to receive has exceeded the maximum transfer size ("452")
	 */
	public Object receiveFromDataChannel() {
		if(!isDataChannelOpen) {
			try {
				establishDataConnection();
			}catch(SocketTimeoutException e) {
				sendReply("426 Transfer aborted; Data Connection Timed Out");
			}catch (IOException e) {
				sendReply("425 Can't Open Data Connection");
			}
		}
		
		Object data = null;
		if(isDataChannelOpen){
			try {
				data = receiveData();
			}catch(TransferSizeExceededException e) {
				sendReply("452 Transfer aborted; Maximum Transfer Size Exceeded");
			}catch (IOException e) {
				sendReply("426 Transfer aborted");
			}finally {
				try {
					closeDataConnection();
				}catch(IOException e) {
					System.err.println("FTP Server Thread: Could Not Close Data Connection");
				}
			}
		}
		return data;
	}
	
	
	/*
	 * Establishes the data connection between the server and the client
	 * 
	 * Throws:
	 * SocketTimeoutException	if either the server or the client data socket times out
	 * IOException				if the data connection can't be established
	 */
	public void establishDataConnection() throws SocketTimeoutException, IOException{
		if(isActiveMode)
			dataClientSocket = new Socket(ipClient, portClient);
			
		else {
			dataServerSocket = new ServerSocket(DATA_SERVER_PORT);
			dataServerSocket.setSoTimeout(TIMEOUT);
			dataClientSocket = dataServerSocket.accept();
		}
		
		dataClientSocket.setSoTimeout(TIMEOUT);
		dataClientSocket.setTcpNoDelay(true);
		dataClientSocket.setReuseAddress(true);
		isDataChannelOpen = true;
	}
	
	
	/*
	 * Transfers string data through the data channel to the client
	 * 
	 * Arguments:
	 * data		the ASCII data to transfer to the client
	 * 
	 * Throws:
	 * IOException		if the data can't be written to the client data socket
	 */
	public void transferData(String data) throws IOException { 
		OutputStream out = dataClientSocket.getOutputStream();
		DataOutputStream dataOutputStream = new DataOutputStream(out);
		dataOutputStream.writeBytes(data + "\r\n");
		dataOutputStream.flush();
	}
	
	
	/*
	 * Transfers data from a file through the data channel to the client
	 * 
	 * Arguments:
	 * file		the virtual file whose content must be transfered
	 * 
	 * Throws:
	 * IOException		if the data can't be written to the client data socket
	 */
	public void transferData(VirtualFile<?> file) throws IOException { 
		OutputStream out = dataClientSocket.getOutputStream();
		
		if(isBinaryTransferType) { // if Binary Transfer Type	
			out.write((byte[])file.getContent());
			out.flush();
			
		}else {					   // if ASCII Transfer Type
			OutputStreamWriter outputStreamWriter = new OutputStreamWriter(out, StandardCharsets.UTF_8);
			String content = file.getContent().toString();
			outputStreamWriter.write(content, 0, content.length());
		    outputStreamWriter.flush();			
		}
	}
	
	
	/*
	 * Receives data through the data channel from the client
	 * 
	 * Return:
	 * the data received from the client
	 * 
	 * Throws:
	 * IOException						if the data can't be read from the client data socket
	 * TransferSizeExceededException	if the data to receive has exceed the maximum transfer size 
	 */
	public Object receiveData() throws IOException, TransferSizeExceededException {
		InputStream in = dataClientSocket.getInputStream();
		
		if(isBinaryTransferType) { // if Binary Transfer Type
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			int readLength = 0;
			byte[] data = new byte[VirtualDirectoryContent.MAX_FILE_SIZE + 1];
			while ((readLength = in.read(data, 0, data.length)) != -1) {
				buffer.write(data, 0, readLength);
			}
			
			if(buffer.size() > VirtualDirectoryContent.MAX_FILE_SIZE) {
				throw new TransferSizeExceededException("Maximum Transfer Size (" 
						+ VirtualDirectoryContent.MAX_FILE_SIZE 
						+ " bytes) Exceeded");
			}
			
			data = buffer.toByteArray();
			return (Object)data;
			
		}else {				 	   // if ASCII Transfer Type
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in));
			String data = null;
			String line = bufferedReader.readLine();
			if(line != null)
				data = "";
			
			while(line != null && data.length() < VirtualDirectoryContent.MAX_FILE_SIZE) {
				data += line;
				line = bufferedReader.readLine();
			}
			
			if(line != null) {
				throw new TransferSizeExceededException("Maximum Transfer Size (" 
														+ VirtualDirectoryContent.MAX_FILE_SIZE 
														+ " bytes) Exceeded");
			}
			return (Object)data;
		}
	}
	
	
	/*
	 * Close the data connection between the server and the client
	 * 
	 * Throws:
	 * IOException		if the data connection can't be closed
	 */
	public void closeDataConnection() throws IOException{
		isDataChannelOpen = false;
		dataClientSocket.close();
		if(!isActiveMode)
			dataServerSocket.close();
	}
	
}
