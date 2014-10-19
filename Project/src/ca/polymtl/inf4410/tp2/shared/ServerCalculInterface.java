package ca.polymtl.inf4410.tp2.shared;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Hashtable;

/**
 * <p>Le serveur de calcul va exposer aux serverRepartiteurs les opérations suivantes : 
 * <ul>
 * 	 <li><strong>generateserverRepartiteurid()</strong></li>
 * 	 <li><em>generateserverRepartiteurid(int serverRepartiteurId) - non exigée mais nous simplifie la vie pour la gestion de l'identifiant du serverRepartiteur.</em></li>
 *   <li><strong>create</strong>(String nom)</li>
 *   <li>Hashtable<String, Integer> <strong>list</strong>()</li>
 *   <li>Fichier <strong>get</strong>(String nom)</li>
 *   <li>Fichier <strong>lock</strong>(String nom, int serverRepartiteurid)</li>
 *   <li>boolean <strong>push</strong>(String nom, byte[] contenu, int serverRepartiteurid)</li>
 * </ul></p>
 * @author Antoine Giraud #1761581
 *
 */
public interface ServerCalculInterface extends Remote {
	int compute(Tache task) throws RemoteException;
}
