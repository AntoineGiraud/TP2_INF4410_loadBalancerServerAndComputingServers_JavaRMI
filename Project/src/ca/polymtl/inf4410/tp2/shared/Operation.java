package ca.polymtl.inf4410.tp2.shared;

import java.io.Serializable;

/**
 * Cette classe permet de faire le lien entre les opérations données par le TP et créer un véritable objet pour gérer les opérations et les passer facilement au serveur dans des Taches.
 * @author Antoine
 *
 */
public class Operation implements Serializable {
	private static final long serialVersionUID = -5919711688941361027L;
	public String name;
	public int value;
	
	/** constructeur de la classe Operation */
	public Operation(String name, int value) throws OperationUnknownException {
		if (!name.equals("fib") && !name.equals("prime"))
			throw new OperationUnknownException(name);
		this.name = name;
		this.value = value;
	}
	/** lancer le calcul de cette opération. */
	public int compute() throws OperationUnknownException {
		if (this.name.equals("fib"))
			return Operations.fib(this.value);
		else if (this.name.equals("prime"))
			return Operations.prime(this.value);
		else
			throw new OperationUnknownException(this.name);
	}
}
