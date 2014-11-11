package ca.polymtl.inf4410.tp2.serverRepartiteur;

import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

import ca.polymtl.inf4410.tp2.shared.ServerCalculInterface;

/**
 * <p>Cette classe va nous permettre de séparer les fonctions de calcul côté serverRepartiteur et toute la gestion de la liste des serveurs de calcul.<br>
 * Cette classe va proposer un certain nombre de fonctions utiles pour gérer la liste des serveurs de Calcul.</p>
 * @author Antoine
 *
 */
public class ServerCalculStubsManager {

	private Hashtable<String, ServerCalculInterface> ServerDispos = null;
	private Random rand = new Random();
	private Thread updateServerListThread;
	private String[] rmiRegistryIpsToCheck;

	public ServerCalculStubsManager(String[] rmiRegistryIpsToCheck) {
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}
		
		this.ServerDispos = new Hashtable<String, ServerCalculInterface>();
		this.rmiRegistryIpsToCheck = (rmiRegistryIpsToCheck != null && rmiRegistryIpsToCheck.length != 0)?rmiRegistryIpsToCheck:new String[]{"127.0.0.1"};
		refreshServerList();
		
	}

	public boolean hasServers() {
		return !ServerDispos.isEmpty();
	}
	public boolean hasServer(String serverName) {
		return (ServerDispos.containsKey(serverName))?true:false;
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

		for (String RmiIp : rmiRegistryIpsToCheck) {
			try {
				Registry registry = LocateRegistry.getRegistry(RmiIp, 5000);
				String[] listeServeurs = registry.list();
				if (listeServeurs.length > 0 && registry != null) {
					for (String serverName : listeServeurs) {
						ServerCalculInterface stub = null;
						try {
							stub = (ServerCalculInterface) registry.lookup(serverName);
						} catch (AccessException e) { System.out.println("AccessException for" + serverName);}
						  catch (RemoteException e) { System.out.println("RemoteException for" + serverName);}
						  catch (NotBoundException e){System.out.println("NotBoundException for"+serverName);}
						try {
							if (stub != null && stub.ping()) {
								if (ServerDispos.containsKey(serverName))
									System.out.println("Attention, un "+serverName+" est déjà déclaré dans la liste des serveurs");
								else{
									ServerDispos.put(serverName, stub);
									if (!oldServeurs.containsKey(serverName))
										addedAndRemovedServers += "+"+serverName + ", ";
								}
							} else { // Je ne devrais jamais arriver ici, il sertait tombé dans le Catch
								System.out.println(serverName + " non joignable dans le RMI à l'IP "+RmiIp);
								if (RmiIp == "127.0.0.1")
									unbind(serverName, registry); // On le retire de la liste du RMI car on a pas réussi à le joindre.
							}
						} catch (RemoteException e1) {
							System.out.println(serverName + " non joignable dans le RMI à l'IP "+RmiIp);
							if (RmiIp == "127.0.0.1")
								unbind(serverName, registry); // On le retire de la liste du RMI car on a pas réussi à le joindre.
						}
					}
				}
			} catch (RemoteException e) { System.out.println("Erreur de connexion au Registre RMI : "+RmiIp+" \n "+e.getMessage()); }
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
	public void unbind(String serverNameToUnbind, Registry registry ) {
		try {
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
	 * @return String nom d'un serveur dans la liste Hastable des serveurs disponibles.
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
	/**
	 * Même fonction que getRandomServerName() sauf que l'on s'assurera de ne pas retourner le nom de serveur passé en paramêtre
	 * @param dontPickThisServer
	 * @return String nom d'un serveur dans la liste Hastable des serveurs disponibles.
	 */
	public synchronized String getRandomServerName(String dontPickThisServer) {
		if (!this.hasServers()) throw new ArrayIndexOutOfBoundsException("Notre liste de serveurs est vide"); // On retourne une chaine vide si notre liste de serveurs est nul. On réessayera plus tard !
 
		Hashtable<String, ServerCalculInterface> ServerDispos = new Hashtable<String, ServerCalculInterface>(this.ServerDispos);
		ServerDispos.remove(dontPickThisServer);
		if (ServerDispos.isEmpty()) throw new ArrayIndexOutOfBoundsException("Il n'y a pas d'autres serveurs dans la liste hormis celui passé en paramêtre.");
		
		int random = this.rand.nextInt(ServerDispos.size());
		Set<String> set = ServerDispos.keySet();
		String serverName = null;
		Iterator<String> serverNames = set.iterator();
		for (int j = 0; j <= random; j++)
			serverName = serverNames.next(); // On retourne le stub du server numero random
		return serverName;
	}
	/**
	 * Même fonction que getRandomServerName() sauf que l'on s'assurera de ne pas retourner le nom de serveur passé en paramêtre
	 * @param findServersInvolved ArrayList<String> Liste de serveurs que l'on ne veut pas avoir
	 * @return String nom d'un serveur dans la liste Hastable des serveurs disponibles.
	 */
	public synchronized String getRandomServerName(ArrayList<String> findServersInvolved) {
		if (!this.hasServers()) throw new ArrayIndexOutOfBoundsException("Notre liste de serveurs est vide"); // On retourne une chaine vide si notre liste de serveurs est nul. On réessayera plus tard !
		
		Hashtable<String, ServerCalculInterface> ServerDispos = new Hashtable<String, ServerCalculInterface>(this.ServerDispos);
		for (String serverName : findServersInvolved) {
			if (ServerDispos.containsKey(serverName))
				ServerDispos.remove(serverName);
		}
		if (ServerDispos.isEmpty()) throw new ArrayIndexOutOfBoundsException("Il n'y a pas d'autres serveurs dans la liste moins ceux passés en paramêtre.");
		
		int random = this.rand.nextInt(ServerDispos.size());
		Set<String> set = ServerDispos.keySet();
		String serverName = null;
		Iterator<String> serverNames = set.iterator();
		for (int j = 0; j <= random; j++)
			serverName = serverNames.next(); // On retourne le stub du server numero random
		return serverName;
	}

	/** Obtenir un serveur au hazard */
	public synchronized ServerCalculInterface getRandomStub() {
		return ServerDispos.get(getRandomServerName());
	}

	/** Stope notre thread qui faisait une Mise A Jour régulière de notre liste de serveurs. */
	public synchronized void interruptServersMajWatch() { this.updateServerListThread.interrupt(); }
	/** Lance notre thread qui va faire une Mise A Jour régulière de notre liste de serveurs. */
	public synchronized void startServersMajWatch() {
		updateServerListThread = new Thread() {
			public void run() {
				while (!this.isInterrupted()) {
					try { sleep(2000);}
					catch (InterruptedException e) { this.interrupt(); }
					refreshServerList();
				}
			}
		};
		updateServerListThread.start();
	}

	public synchronized int getServersCount() {
		return this.ServerDispos.size();
	}
	
	public synchronized boolean checkServerAndMaybeRefreshList(String serverName) {
		try {
			if (this.hasServer(serverName) && this.get(serverName).ping()) { 
				return true;
			}else this.refreshServerList(); // Le serveur n'est plus joignable => refresh
		} catch (RemoteException e) { this.refreshServerList(); } // Le serveur n'est plus joignable => refresh
		if (this.hasServer(serverName)) {
			try {
				return this.get(serverName).ping();
			} catch (RemoteException e) {return false;}
		}else{
			return false;
		}
	}
	
	public synchronized void checkServersAndMaybeWaitForMore() {
		if (!this.hasServers()) { // On n'a plus de serveurs connectés !!
			this.interruptServersMajWatch();
			System.out.println("Aucuns serveurs de calcul disponibles. Veuillez connecter au moins un serveur de calcul pour reprendre le calcul.");
			int waitTime = 50;
			while (!this.hasServers()) {
				try { Thread.sleep(waitTime); /* petite pause */ } catch (InterruptedException e) { e.printStackTrace();	}
				this.refreshServerList();
				if (waitTime < 3000) waitTime *=3; // on augmente le temps de refresh jq atteindre un temps de refresh de 2s
			}
			this.startServersMajWatch();
		}
	}
	/**
	 * 
	 * @param atLeastThisMuchServers int on doit au moins avoir 3 serveurs.
	 * @return boolean retourne false si l'on est pas en dessus du nb de serveur voulu. true si on doit attendre que l'on reconnecte un serveur.
	 */
	public synchronized boolean checkHasServersAndMaybeWaitForMore(int atLeastThisMuchServers) {
		if (!this.hasServers() || (this.hasServers() && this.ServerDispos.size() < atLeastThisMuchServers)) { // On n'a plus de serveurs connectés !!
			this.interruptServersMajWatch();
			System.out.println("Aucuns serveurs de calcul disponibles. Veuillez connecter au moins "+atLeastThisMuchServers+" serveur de calcul pour reprendre le calcul.");
			int waitTime = 50;
			while (!this.hasServers() || (this.hasServers() && this.ServerDispos.size() < atLeastThisMuchServers)) {
				try {Thread.sleep(500); /* petite pause */ } catch (InterruptedException e) { e.printStackTrace();	}
				this.refreshServerList();
				if (waitTime < 3000) waitTime *=3; // on augmente le temps de refresh jq atteindre un temps de refresh de 2s
			}
			this.startServersMajWatch();
			return true;
		}
		return false;
	}

	public synchronized String checkHasServersAndMaybeWaitForMore(ArrayList<String> serversInvolved) {
		int serverListSize = this.ServerDispos.size();
		String randomServerName = null;
		try {
			randomServerName = this.getRandomServerName(serversInvolved);
		} catch (ArrayIndexOutOfBoundsException e) {/*On a tjs pas assez de serveurs*/}
		if (randomServerName == null) { // On n'a plus de serveurs connectés !!
			this.interruptServersMajWatch();
			int waitTime = 50;
			while (randomServerName == null) {
				try {Thread.sleep(500); /* petite pause */ } catch (InterruptedException e) { e.printStackTrace();	}
				if (waitTime < 3000) waitTime *=3; // on augmente le temps de refresh jq atteindre un temps de refresh de 2s
				this.refreshServerList();
				try {
					randomServerName = this.getRandomServerName(serversInvolved);
				} catch (ArrayIndexOutOfBoundsException e) {/*On a tjs pas assez de serveurs*/}
				if (randomServerName == null && serverListSize != this.ServerDispos.size()) {
					serverListSize = this.ServerDispos.size();
					System.out.println("Nous avons de plus besoin d'un serveur de plus pour déterminer le résultat.");
					System.out.println("Veuillez ajouter un nouveau serveur non malicieux.");
				}
			}
			this.startServersMajWatch();
			return randomServerName;
		}
		return null;
	}
}