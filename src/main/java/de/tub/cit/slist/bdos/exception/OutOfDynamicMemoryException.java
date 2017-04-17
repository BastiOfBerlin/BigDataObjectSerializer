package de.tub.cit.slist.bdos.exception;

public class OutOfDynamicMemoryException extends Exception {

	private static final long serialVersionUID = -2042481746011731247L;

	public OutOfDynamicMemoryException() {
		super();
	}

	public OutOfDynamicMemoryException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public OutOfDynamicMemoryException(final String message, final Throwable cause) {
		super(message, cause);
	}

	public OutOfDynamicMemoryException(final String message) {
		super(message);
	}

	public OutOfDynamicMemoryException(final Throwable cause) {
		super(cause);
	}

}
