package ca.polymtl.inf4410.tp2.shared;

import java.io.Serializable;


public class Operation implements Serializable {
	private static final long serialVersionUID = -5919711688941361027L;
	public String name;
	public int value;
	
	
	public Operation(String name, int value) throws OperationUnknownException {
		if (!name.equals("fib") && !name.equals("prime"))
			throw new OperationUnknownException(name);
		this.name = name;
		this.value = value;
	}
	
	public int compute() throws OperationUnknownException {
		if (this.name.equals("fib"))
			return Operations.fib(this.value);
		else if (this.name.equals("prime"))
			return Operations.prime(this.value);
		else
			throw new OperationUnknownException(this.name);
	}
}
