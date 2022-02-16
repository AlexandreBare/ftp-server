/*
 * Virtual File Class
 * Manages a virtual file and the operations that it can undergo
 */
public class VirtualFile<T> {
	// Constant values
	private static final String ACCESS_RIGHTS = "-rw-r--r-- 1";
	private static final String OWNER = "FTPServerBastienAlexandre";
	
	//
	private String filename;
	private T content;
	private long modificationTime;
	private int size;
	
	
	/*
	 * Constructor 
	 * -> Initializes a virtual file
	 * 
	 * Arguments:
	 * filename			the name of the virtual file
	 * content			the content of the file (either of type byte[] 
	 * 					for binary files or String for text files)
	 * size				the size of the file (in bytes)
	 * 
	 * Throws:
	 * InvalidStringFormatException if the filename is null, empty 
	 * or contains non ASCII characters or '/'
	 */
	public VirtualFile(String _filename, T _content, int _size) 
			throws InvalidStringFormatException, TransferSizeExceededException{
		if(_filename != null && !_filename.matches("^[\\p{ASCII}&&[^/]]+$")) {
			throw new InvalidStringFormatException("Empty Filename Or "
					+ "Non ASCII And '/' Characters In Filename Not Allowed");
		}
		
		if(_size > VirtualDirectoryContent.MAX_FILE_SIZE) {
			throw new TransferSizeExceededException("Maximum File Size (" 
					+ VirtualDirectoryContent.MAX_FILE_SIZE 
					+ " bytes) Exceeded");
		}
		
		filename = _filename;
		content = _content;
		size = _size;
		modificationTime = System.currentTimeMillis();
	}
	
	
	/*
	 * Return:
	 * the content of the file
	 */
	public synchronized T getContent() {
		return content;
	}
	
	
	/*
	 * Return:
	 * the size (in bytes) of the file
	 */
	public synchronized long getSize() {
		return size;
	}
	
	
	/*
	 * Return:
	 * the filename
	 */
	public synchronized String getFilename() {
		return filename;
	}
	
	
	/*
	 * Return:
	 * the modification time of the file, i.e. the last time 
	 * it was modified in milliseconds since 1st January 1970
	 */
	public synchronized long getModificationTime() {
		return modificationTime;
	}
	
	
	/*
	 * Return:
	 * the access rights of the file
	 */
	public synchronized String getAccessRights() {
		return ACCESS_RIGHTS;
	}
	
	
	/*
	 * Return:
	 * the owner of the file
	 */
	public synchronized String getOwner() {
		return OWNER;
	}
	
	
	/*
	 * Renames a file
	 * 
	 * Arguments:
	 * newFilename		the new filename
	 * 
	 * Return:
	 * true if the file was correctly renamed, false otherwise
	 */
	public synchronized boolean renameTo(String newFilename) {
		if(newFilename != null && newFilename.matches("^[\\p{ASCII}&&[^/]]+$")) { 
			// A filename can not contain the character '/' 
			// or non ASCII characters and can not be empty
			
			filename = newFilename;
			modificationTime = System.currentTimeMillis();
			return true;
		}
		
		return false;
	}
}
