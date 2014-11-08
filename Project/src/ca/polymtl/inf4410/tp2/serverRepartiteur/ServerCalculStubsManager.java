package ca.polymtl.inf4410.tp2.serverRepartiteur;

import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

import ca.polymtl.inf4410.tp2.shared.ServerCalculInterface;

public class ServerCalculStubsManager {

	private Hashtable<String, ServerCalculInterface> ServerDispos = null;
	private Random rand = new Random();
	private Thread updateServerListThread;

	public ServerCalculStubsManager() {
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}
		
		this.ServerDispos = new Hashtable<String, ServerCalculInterface>();
		refreshServerList();
		
		this.updateServerListThread = new Thread() {
			public void run() {
				while (!this.isInterrupted()) {
					try {
						sleep(2000);
					} catch (InterruptedException e) {
						this.interrupt();
					}
					refreshServerList();
				}
			}
		};
	}
	
	public boolean hasServers() {
		return !ServerDispos.isEmpty();
	}

	public ServerCalculInterface get(String serverName) {
		if (ServerDispos.containsKey(serverName)) return ServerDispos.get(serverName);
		else throw new NoSuchElementException(serverName+" n'est pas dans notre liste de serveurs");
	}

	/**
	 * Se charge de mettre à jour la liste des serveurs qui sont disponibles dans le RMI.
	 */
	public synchronized void refreshServerList() {
		Hashtable<String, ServerCalculInterface> oldServeurs = new Hashtable<String, ServerCalculInterface>();
		oldServeurs.putAll(this.ServerDispos);
		String addedAndRemovedServers = "";
		this.ServerDispos = new Hashtable<String, ServerCalculInterface>();
		String[] listeServeurs = null;
		Registry registry = null;

		try {
			registry = LocateRegistry.getRegistry("127.0.0.1", 5000);
			listeServeurs = registry.list();
		} catch (RemoteException e) { System.out.println("Erreur de connexion au Registre RMI : "+ e.getMessage()); }

		if (listeServeurs.length > 0 && registry != null) {
			for (String serverName : listeServeurs) {
				ServerCalculInterface stub = null;
				try {
					stub = (ServerCalculInterface) registry.lookup(serverName);
				} catch (AccessException e) { System.out.println("AccessException"); }
				  catch (RemoteException e) { System.out.println("RemoteException"); }
				  catch (NotBoundException e){System.out.println("NotBoundException");}
				try {
					if (stub != null && stub.ping()) {
						ServerDispos.put(serverName, stub);
						if (!oldServeurs.containsKey(serverName))
							addedAndRemovedServers += "+"+serverName + ", ";
					} else { // Je ne devrais jamais arriver ici, il sertait tombé dans le Catch
						System.out.print(serverName + " non joignable. ");
						unbind(serverName); // On le retire de la liste du RMI car on a pas réussi à le joindre.
					}
				} catch (RemoteException e1) {
					System.out.print(serverName + " non joignable. ");
					unbind(serverName); // On le retire de la liste du RMI car on a pas réussi à le joindre.
				}
			}
			if (!oldServeurs.isEmpty()) { // On trouve les serveurs qui ont été coupés
				// On récupère les clés de notre Hashtable, id est les serverNames
				Set<String> set = oldServeurs.keySet();
				String serverName;
				Iterator<String> serverNames = set.iterator();
				while (serverNames.hasNext()) {
					serverName = serverNames.next();
					if (!ServerDispos.containsKey(serverName))
						addedAndRemovedServers += "-"+serverName + ", ";
				}
			}
			if (!addedAndRemovedServers.isEmpty())
				System.out.println("## MAJ Liste servers : " + addedAndRemovedServers.substring(0, (addedAndRemovedServers.length() >= 2) ? addedAndRemovedServers.length() - 2 : 0) );
		}
	}
	
	/**
	 * Affiche dans la console la liste des serveur disponibles dans le RMI.
	 */
	public synchronized void listServers() {
		// refreshServerList();
		if (this.hasServers()) {
			int i = 1;
			System.out.println(ServerDispos.size() + " serveur" + (ServerDispos.size() > 1 ? "s" : "") + " connecté" + (ServerDispos.size() > 1 ? "s" : "") + " :");

			// On récupère les clés de notre Hashtable, id est les serverNames
			Set<String> set = ServerDispos.keySet();
			String serverName;
			Iterator<String> serverNames = set.iterator();
			while (serverNames.hasNext()) {
				serverName = serverNames.next();
				System.out.println("Server #" + i + " : " + serverName);
				i++;
			}
		} else System.out.println("Aucuns Serveurs n'est pour l'instant enregistré dans le RMI.");
	}
	
	/**
	 * Pour retirer (unbind) manuelement un serveur enregistré dans le RMI.<br>
	 * Cette fonction a été utilie au tout début du projet.
	 * @param serverNameToUnbind String, nom du serveur à retirer du RMI.
	 */
	public void unbind(String serverNameToUnbind) {
		try {
			Registry registry = LocateRegistry.getRegistry("127.0.0.1", 5000);
			String[] listeServeurs = registry.list();
			if (listeServeurs.length > 0)
				for (String serverName : listeServeurs)
					if (serverName.equals(serverNameToUnbind))
						try{
							registry.unbind(serverNameToUnbind);
							System.out.println(serverNameToUnbind
									+ " a été retiré du RMI Registery");
						}catch (NotBoundException e) { e.printStackTrace();}
		} catch (AccessException e) { System.out.println("Erreur: " + e.getMessage());
		} catch (RemoteException e) { System.out.println("Erreur: " + e.getMessage());}
	}
	
	/**
	 * Pour obtenir au hazard le nom d'un serveur à contacter.<br>
	 * <em>Vous aurez ensuite plus qu'à faire une YourServerCalculManager.get(yourRandomServerName)</em>
	 * @return String nom du serveur dans la liste Hastable des serveurs disponibles dans le RMI.
	 */
	public synchronized String getRandomServerName() {
		if (!this.hasServers()) throw new ArrayIndexOutOfBoundsException("Notre liste de serveurs est vide"); // On retourne une chaine vide si notre liste de serveurs est nul. On réessayera plus tard !
		int random = this.rand.nextInt(ServerDispos.size());
		Set<String> set = ServerDispos.keySet();
		String serverName = null;
		Iterator<String> serverNames = set.iterator();
		for (int j = 0; j <= random; j++)
			serverName = serverNames.next(); // On retourne le stub du server numero random
		return serverName;
	}
	/** Obtenir un serveur au hazard */
	public ServerCalculInterface getRandomStub() {
		return ServerDispos.get(getRandomServerName());
	}

	/** Stope notre thread qui faisait une Mise A Jour régulière de notre liste de serveurs. */
	public void interruptServersMajWatch() { this.updateServerListThread.interrupt(); }
	/** Lance notre thread qui va faire une Mise A Jour régulière de notre liste de serveurs. */
	public void startServersMajWatch() { this.updateServerListThread.start(); }
}
