package ca.polymtl.inf4410.tp2.shared;

public class OperationUnknownException extends Exception {
	public OperationUnknownException(String OperationName){
		System.out.println("L'opération "+OperationName+" n'existe pas.");
	}
}
