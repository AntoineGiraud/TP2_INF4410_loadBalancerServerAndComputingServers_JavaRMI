package ca.polymtl.inf4410.tp2.shared;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * <p>Le serveur de calcul va exposer aux serverRepartiteurs les opérations suivantes : 
 * <ul>
 * 	 <li>int <strong>secureCompute</strong>(Tache task)</li>
 * 	 <li>int <strong>nonSecureCompute</strong>(Tache task)</li>
 *   <li>ping()</li>
 * </ul>
 * Pour plus d'informations sur les exigences du projet, veuillez vous reporter au sujet du TP et à la FAQ.</p>
 * @author Antoine Giraud #1761581
 *
 */
public interface ServerCalculInterface extends Remote {
	/**
	 * <p>fonction qui ve permettre de réaliser le calcul séurisé de tâches passées en paramètre.<br>
	 * Si un server de calcul est malicieux, il se comportera tout de même comme sécurisé en appelant cette fonction ci de calcul.<br>
	 * Gère bien le Taux de refus qui se base sur la formule <em>double T = (double)(task.getNbOperations()-quantiteRessources)/(9*quantiteRessources);</em>
	 * </p>
	 * @param task Tache
	 * @return
	 * @throws RemoteException
	 */
	Tache secureCompute(Tache task) throws RemoteException;
	/**
	 * <p>fonction qui ve permettre de réaliser le calcul en mode non séurisé de tâches passées en paramètre.<br>
	 * Gère bien le cas d'un serveur de calcul malicieux. génère un nombre aléatoire et le compage au pourcentage défini en paramètre.<br>
	 * Gère bien le Taux de refus qui se base sur la formule <em>double T = (double)(task.getNbOperations()-quantiteRessources)/(9*quantiteRessources);</em>
	 * </p>
	 * @param task Tache
	 * @return
	 * @throws RemoteException
	 */
	Tache nonSecureCompute(Tache task) throws RemoteException;
	/**
	 * <p>Fonction toute bête qui ne fait que retourner true si elle est appelée.<br>
	 * Elle va nous permettre de déterminer si la connection au serveur est toujours active.</p>
	 * @return toujours true
	 * @throws RemoteException Si l'on arrive pas à se connecter au serveur, on récupère une RemoteException. 
	 */
	boolean ping() throws RemoteException;
}
