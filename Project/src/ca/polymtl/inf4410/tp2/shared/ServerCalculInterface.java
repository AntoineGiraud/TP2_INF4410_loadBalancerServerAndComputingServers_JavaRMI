package ca.polymtl.inf4410.tp2.shared;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * <p>Le serveur de calcul va exposer aux serverRepartiteurs les opérations suivantes : 
 * <ul>
 * 	 <li>int <strong>compute</strong>(Tache task)</li>
 * </ul></p>
 * @author Antoine Giraud #1761581
 *
 */
public interface ServerCalculInterface extends Remote {
	Tache compute(Tache task) throws RemoteException;
	boolean ping() throws RemoteException;
	Tache nonSecureCompute(Tache task) throws RemoteException;
}
