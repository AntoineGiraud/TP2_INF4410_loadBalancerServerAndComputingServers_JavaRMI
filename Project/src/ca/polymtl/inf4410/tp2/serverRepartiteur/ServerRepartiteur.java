package ca.polymtl.inf4410.tp2.serverRepartiteur;

import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;

import ca.polymtl.inf4410.tp2.shared.ServerCalculInterface;

/**
 * <p>
 * Le serverRepartiteur exposera les commandes list et get pour consulter les fichiers du serveur.<br>
 * La commande create permettra de créer un nouveau fichier.<br>
 * Pour modifier un fichier, l'utilisateur devra d'abord le verrouiller à l'aide de la commande lock.<br>
 * Il appliquera ensuite ses modifications au fichier local et les publiera sur le serveur à l'aide la commande push.<br>
 * Notons qu'un seul serverRepartiteur peut verrouiller un fichier donné à la fois.<br>
 * Aussi, la commande lock téléchargera toujours la dernière version du fichier afin d'éviter que l'utilisateur n'applique ses modifications à une version périmée.<br>
 * </p>
 * @author Antoine Giraud #1761581
 *
 */
public class ServerRepartiteur {
	public static void main(String[] args) {
		ServerRepartiteur serverRepartiteur = new ServerRepartiteur();
		if (args.length > 0) {
			functions f = functions.valueOf(args[0]);
			switch (f){
				case listServers: serverRepartiteur.listServers(); break;
				case compute: serverRepartiteur.compute(args[1]); break;
				case unbind: serverRepartiteur.unbind(args[1]); break;
				default: System.out.println("Mauvaise commande");
			}
		}else
			System.out.println("entrer une command comme argument");
	}

	private ArrayList<ServerCalculInterface> ServerDispos;
	private enum functions { listServers, compute, unbind};

	public ServerRepartiteur() {
		super();
		if (System.getSecurityManager() == null) {
	        System.setSecurityManager(new SecurityManager());
	    }
		
		ServerDispos = new ArrayList<ServerCalculInterface>();
	}

	private void listServers() {
		refreshServerList();
		if (!ServerDispos.isEmpty()) {
			int i = 1;
			for (ServerCalculInterface serverCalcul : ServerDispos) {
				System.out.println("Server #"+i+" :"+serverCalcul.toString());
				i++;
			}
		}else{
			System.out.println("Aucuns Serveurs n'est pour l'instant enregistré dans le RMI.");
		}
		
	}
	private void unbind(String serverNameToUnbind) {
		try {
			Registry registry = LocateRegistry.getRegistry("127.0.0.1",5000);
			String[] listeServeurs = registry.list();
			if (listeServeurs.length > 0) {
				for (String serverName : listeServeurs) {
					if (serverName.equals(serverNameToUnbind)) {
						try {
							registry.unbind(serverNameToUnbind);
							System.out.println(serverNameToUnbind+" a été retiré du RMI Registery");
						} catch (NotBoundException e) { e.printStackTrace(); }
					}
				}
				
			}
		} catch (AccessException e) {
			System.out.println("Erreur: " + e.getMessage());
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		}
	}

	private void refreshServerList() {
		try {
			Registry registry = LocateRegistry.getRegistry("127.0.0.1",5000);
			String[] listeServeurs = registry.list();
			if (listeServeurs.length > 0) {
				for (String serverName : listeServeurs) {
					try {
						ServerDispos.add((ServerCalculInterface) registry.lookup(serverName));
						System.out.print(serverName+", ");
					} catch (NotBoundException e) { e.printStackTrace(); }
				}
				System.out.println(" ont/a été ajoutés à la liste des servers de calcul");
			}
		} catch (AccessException e) {
			System.out.println("Erreur: " + e.getMessage());
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		}
	}

	private void compute(String string) {
		
	}
}
