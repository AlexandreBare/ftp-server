/*
 * Transfer Size Exceeded Exception Class
 * Manages exception when data transfer size is exceeded
 */
public class TransferSizeExceededException extends Exception {
	private static final long serialVersionUID = -3587961497088513078L;

	public TransferSizeExceededException(String message) {
		super(message);
	}
}
