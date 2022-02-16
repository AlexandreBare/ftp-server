/*
 * Invalid String Format Exception Class
 * Manages exception for invalid string format 
 */
public class InvalidStringFormatException extends Exception {
	private static final long serialVersionUID = -8205235306347463917L;

	public InvalidStringFormatException(String message) {
		super(message);
	}
}
