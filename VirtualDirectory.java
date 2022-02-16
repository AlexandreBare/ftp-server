import java.text.SimpleDateFormat;
import java.util.*;

/*
 * Virtual Directory Class
 * Manages a virtual directory and 
 * the operations that can be performed on it
 */
public class VirtualDirectory {
	// Constant values
	private static final String ACCESS_RIGHTS = "drwxr-xr-x 1";
	private static final String OWNER = "FTPServerBastienAlexandre";
	private static final String SIZE_FORMAT = "            "; // 12 spaces
	
	//
	private String directoryName;
	private VirtualDirectory parentDirectory;
	private Dictionary<String, VirtualFile<?>> files;
	private Dictionary<String, VirtualDirectory> subDirectories;
	private long modificationTime; // The last time the directory was modified
	private boolean isProtected; // Whether the directory is protected or not
	/*
	 * Note:
	 * "Protected" is a property of a directory for not being 
     * accessible in an unprotected connection 
     * (eg: in the case of an anonymous user where the connection
     * is not protected by a password)
     */
	
	
	/*
	 * Constructor
	 * Initializes the hard-coded content of the virtual directory
	 */
	public VirtualDirectory() {
		try {
			
			directoryName = "/";
			parentDirectory = null;
			isProtected = false;
			
			files = new Hashtable<String, VirtualFile<?>>();
			VirtualFile<String> myText = new VirtualFile<String>("mytext.txt", VirtualDirectoryContent.MYTEXT,
					VirtualDirectoryContent.MYTEXT.length());
			files.put("mytext.txt", myText);
			VirtualFile<byte[]> myImage = new VirtualFile<byte[]>("myimage.bmp", VirtualDirectoryContent.MYIMAGE, 
					VirtualDirectoryContent.MYIMAGE.length);
			files.put("myimage.bmp", myImage);
			
			subDirectories = new Hashtable<String, VirtualDirectory>();
			ArrayList<VirtualFile<?>> _files = new ArrayList<VirtualFile<?>>();
			VirtualFile<String> secret = new VirtualFile<String>("secret.txt", VirtualDirectoryContent.SECRET,
					VirtualDirectoryContent.SECRET.length());
			_files.add(secret);
			VirtualDirectory subDirectory = new VirtualDirectory("private", this, _files, null, true);
			subDirectories.put(subDirectory.directoryName, subDirectory);
			
			modificationTime = System.currentTimeMillis();
			
		}catch(Exception e) {
			System.err.println("DirectoryNode Exception at Initialisation: " + e);
		}
	}
	
	
	/*
	 * Constructor
	 * 
	 * Arguments:
	 * _directoryName		the name of the directory
	 * _parentDirectory		the parent directory
	 * _files				a list of the files in the directory
	 * _subDirectories		a list of sub-directories
	 * _isProtected			true if the connection is protected, false otherwise
	 * 
	 * Note:
	 * "Protected" is a property of a file or directory for not being 
	 * accessible in an unprotected connection 
	 * (eg: in the case of an anonymous user where the connection
     * is not protected by a password)
	 */
	public VirtualDirectory(String _directoryName, VirtualDirectory _parentDirectory, 
			List<VirtualFile<?>> _files, List<VirtualDirectory> _subDirectories, boolean _isProtected) {
		directoryName = _directoryName;
		parentDirectory = _parentDirectory;
		isProtected = _isProtected;
		
		files = new Hashtable<String, VirtualFile<?>>();
		if(_files != null) {
			for(int i = 0; i < _files.size(); i++) {
				VirtualFile<?> currentFile = _files.get(i);
				String relativePath = currentFile.getFilename();
				files.put(relativePath, currentFile);
			}
		}
		
		subDirectories = new Hashtable<String, VirtualDirectory>();
		if(_subDirectories != null) {
			for(int i = 0; i < _subDirectories.size(); i++) {
				subDirectories.put(_subDirectories.get(i).directoryName, _subDirectories.get(i));
			}
		}
		
		modificationTime = System.currentTimeMillis();
	}
	
	
	/*
	 * Gives the directory absolute path
	 * 
	 * Return:
	 * the directory absolute path
	 */
	public String getDirectoryPath() {
		if(parentDirectory != null)
			return parentDirectory.getDirectoryPath() + directoryName;
		
		return directoryName;
	}
	
	
	/*
	 * Prints/Lists the directory content
	 * 
	 * Arguments:
	 * canAccessProtectedData 	true if protected directories 
	 * 							can be accessed, false otherwise
	 * 
	 * Return:
	 * the directory content in the /bin/ls format for each file/directory:
	 * "AccessRights Owner         size modificationDate filename\r\n"
	 * 
	 * Note:
	 * AccessRights starts with a "-" for a file 
	 * 				   and with a "d" for a directory
	 */
	public String printDirectoryContent(boolean canAccessProtectedDirectories) {
		String directoryContent = "";
			
		for(Enumeration<String> e = files.keys(); e.hasMoreElements();) {
			String filename = e.nextElement();
			VirtualFile<?> file = files.get(filename);
			String size = String.valueOf(file.getSize());
			String padding = SIZE_FORMAT.substring(0, SIZE_FORMAT.length() - size.length());
			SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm");
			String date = sdf.format(file.getModificationTime()); // last modified file date
			
			//File Format: "-rw-r--r-- 1 owner group           213 Aug 26 16:31 README\r\n"
			directoryContent += file.getAccessRights() + " " + file.getOwner() + " " 
								+ padding + size + " " + date + " " + filename + "\r\n";
		}
		
		for(Enumeration<String> e = subDirectories.keys(); e.hasMoreElements();) {
			String dirName = e.nextElement();
			VirtualDirectory dir = subDirectories.get(dirName);
			
			if(!dir.isProtected || canAccessProtectedDirectories) {
				String size = String.valueOf(dirName.length());
				String padding = SIZE_FORMAT.substring(0, SIZE_FORMAT.length() - size.length());
				SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm");
				String date = sdf.format(dir.modificationTime); // last modified directory date
				
				//Directory Format: "drwxr-xr-x 1 owner group           213 Aug 26 16:31 README\r\n"
				directoryContent += ACCESS_RIGHTS + " " + OWNER + " " + padding 
									+ size + " " + date + " " + dirName + "\r\n";
			}
		}
		
		return directoryContent;
	}
	
	
	/*
	 * Renames a file of the directory
	 * 
	 * Arguments:
	 * oldFilename			the old filename
	 * newFilename			the new filename
	 * 
	 * Return:
	 * True if the file was correctly renamed, false otherwise
	 */
	public synchronized boolean renameFile(String oldFilename, String newFilename) {
		if(oldFilename == null || newFilename == null)
			return false;
		
		VirtualFile<?> file = downloadFile(oldFilename);
		if(file == null || !file.renameTo(newFilename) || !uploadFile(file))
			return false;
		
		removeFile(oldFilename);
		modificationTime = System.currentTimeMillis();
		return true;
	}
	
	
	/*
	 * Downloads a file from the directory
	 * 
	 * Arguments:
	 * filename					the name of the file
	 * 
	 * Return:
	 * the downloaded file or null if the requested file can't be found in the directory
	 */
	public synchronized VirtualFile<?> downloadFile(String filename){
		if(filename == null)
			return null;
	
		return files.get(filename);
	}

	
	/*
	 * Uploads a file to the directory
	 * 
	 * Arguments:
	 * file		the file to upload
	 * 
	 * Return:
	 * true if the upload succeeded, false otherwise
	 */
	public synchronized boolean uploadFile(VirtualFile<?> file){
		if(file == null)
			return false;
		
		String filename = file.getFilename();
		return uploadFile(file, filename);
	}
	
	
	/*
	 * Uploads a file to the directory
	 * 
	 * Arguments:
	 * file			the file to upload
	 * filename		the name of the file
	 * 
	 * Return:
	 * true if the upload succeeded, false otherwise
	 */
	private synchronized boolean uploadFile(VirtualFile<?> file, String filename) {
		if(file == null || filename == null)
			return false;
		
		files.put(filename, file);
	
		modificationTime = System.currentTimeMillis();
		return true;
	}
	
	
	/*
	 * Removes a file of the directory
	 * 
	 * Arguments:
	 * filename		the name of the file to delete
	 * 
	 * Return:
	 * the file if it has been deleted from the server, null otherwise
	 */
	public VirtualFile<?> removeFile(String filename){
		if(filename == null)
			return null;
		
		modificationTime = System.currentTimeMillis();
		return files.remove(filename);
	}
	
	
	/*
	 * Gives the parent directory
	 * 
	 * Return:
	 * the parent directory, null if there is none
	 */
	public VirtualDirectory getParentDirectory() {
		return parentDirectory;
	}
	
	
	/*
	 * Gives a specific direct sub-directory from its name
	 * 
	 * Arguments:
	 * directoryName					the name of the direct sub-directory
	 * canAccessProtectedDirectories	true if protected directories 
	 * 									can be accessed, false otherwise
	 * 
	 * Return:
	 * the direct sub-directory asked, 
	 * null if the direct sub-directory can't be found in the directory
	 */
	public VirtualDirectory getSubDirectory(String directoryName, boolean canAccessProtectedDirectories){
		if(directoryName == null)
			return null;
		
		VirtualDirectory directory = subDirectories.get(directoryName);
		if(!directory.isProtected || canAccessProtectedDirectories)
			return directory;
		
		return null;
	}
	
	
	/*
	 * Gives a directory identified by its pathname
	 * 
	 * Arguments:
	 * pathname							the pathname of the directory
	 * canAccessProtectedDirectories	true if protected directories 
	 * 									can be accessed, false otherwise
	 * 
	 * Return:
	 * the directory asked, null if the directory can't be found
	 */
	public VirtualDirectory getDirectory(String pathname, boolean canAccessProtectedDirectories){
		if(pathname == null)
			return null;
					
		if(pathname.contentEquals("/"))
			return this;
		
		if(pathname.charAt(pathname.length() - 1) == '/')
			pathname = pathname.substring(0, pathname.length() - 1);
		
		String firstDirectoryName;
		String endPath;
		int index = pathname.indexOf('/');
		
		if(index >= 0) { // if absolute path
			
			endPath = pathname.substring(index + 1);
			if(index > 0) {
				firstDirectoryName = pathname.substring(0, index - 1);
				VirtualDirectory subDirectory = getSubDirectory(firstDirectoryName, canAccessProtectedDirectories);
				if(subDirectory != null)
					return subDirectory.getDirectory(endPath, canAccessProtectedDirectories);
				else
					return null;
			}else
				return getDirectory(endPath, canAccessProtectedDirectories);
			
		}else if(pathname.length() > 0) // if relative path
			return getSubDirectory(pathname, canAccessProtectedDirectories);
		else // if invalid path
			return null;
	}
	
}
