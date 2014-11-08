package ca.polymtl.inf4410.tp2.shared;

public class OperationUnknownException extends Exception {
	private static final long serialVersionUID = 3150397210537746968L;

	public OperationUnknownException(String OperationName){
		System.out.println("L'opération "+OperationName+" n'existe pas.");
	}
}
