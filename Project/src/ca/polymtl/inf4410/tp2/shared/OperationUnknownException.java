package ca.polymtl.inf4410.tp2.shared;

/**
 * Exception au cas où l'on ai une opération inconue dans les fichiers d'Opérations.
 * @author Antoine
 *
 */
public class OperationUnknownException extends Exception {
	private static final long serialVersionUID = 3150397210537746968L;

	public OperationUnknownException(String OperationName){
		System.out.println("L'opération "+OperationName+" n'existe pas.");
	}
}
