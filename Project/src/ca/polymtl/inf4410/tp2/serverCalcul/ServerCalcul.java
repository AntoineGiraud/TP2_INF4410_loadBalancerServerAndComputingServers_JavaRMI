

package ca.polymtl.inf4410.tp2.serverCalcul;

import java.io.File;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Hashtable;

import ca.polymtl.inf4410.tp2.shared.Fichier;
import ca.polymtl.inf4410.tp2.shared.ServerCalculInterface;

/**
 * <p>confer {@link ServerCalculInterface} pour les explications sur cette classe et ses méthodes.</p>
 * @author Antoine Giraud #1761581
 *
 */
public class ServerCalcul implements ServerCalculInterface {

	public static void main(String[] args) {
		ServerCalcul server = new ServerCalcul();
		server.run();
	}
	
	private ArrayList<Fichier> ListeFichiers;
	private ArrayList<Integer> ListeServerRepartiteurs;
	private String pathServerFolder;

	public ServerCalcul() {
		super();
		ListeFichiers = new ArrayList<Fichier>();
		ListeServerRepartiteurs = new ArrayList<Integer>();
		pathServerFolder = "server_files";
		
		// Mettre à jour la liste des fichiers présents sur le serveur
        getAllFile(new File(pathServerFolder));
	}
	
	/**
	 * <p>Petite fonction récursive qui va nous permettre de parcourir le répertoire de notre serveur où sont situés les fichiers que l'on reçoit des serverRepartiteurs.<br>
	 * Cela va nous permetter de pouvoir garder nos fichiers entre deux sessions.</p>
	 * @param file File notre répertoir et puis ses fichiers - récursivité
	 */
	public void getAllFile(File file) {
        if (file.exists()) {
            if (file.isFile()) {
            	ListeFichiers.add(new Fichier(file.getName(), pathServerFolder));
            } else if (file.isDirectory()) {
                File[] tabTmp = file.listFiles();
                for (File f : tabTmp) {
                    getAllFile(f);
                }
            }
        }
    }
    
	/**
	 * Connection au registre RMI et enregistrement de la classe server
	 */
	private void run() {
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		try {
			ServerCalculInterface stub = (ServerCalculInterface) UnicastRemoteObject
					.exportObject(this, 5010);

			Registry registry = LocateRegistry.getRegistry("127.0.0.1", 5001);
			registry.rebind("serverCalcul", stub);
			System.out.println("ServerCalcul ready.");
		} catch (ConnectException e) {
			System.err.println("Impossible de se connecter au registre RMI. Est-ce que rmiregistry est lancé ?");
			System.err.println();
			System.err.println("Erreur: " + e.getMessage());
		} catch (Exception e) {
			System.err.println("Erreur: " + e.getMessage());
		}
	}

	@Override
	public boolean create(String nom) throws RemoteException {
		for (Fichier f : ListeFichiers) {
			if (f.getNomFichier().contentEquals(nom))
				return false;
		}
		ListeFichiers.add(new Fichier(nom, pathServerFolder));
		System.out.println("Fichier "+nom+" ajouté !");
		return true;
	}

	@Override
	public Hashtable<String, Integer> list() throws RemoteException {
		System.out.println("Liste des fichiers : ");
		Hashtable<String, Integer> liste = new Hashtable<String, Integer>();
		for (Fichier f : ListeFichiers) {
			liste.put(f.getNomFichier(), f.getServerRepartiteurId());
			System.out.println("* "+f.getNomFichier()+" - #"+f.getServerRepartiteurId());
		}
		return liste;
	}

	@Override
	public Fichier get(String nom) throws RemoteException {
		for (Fichier f : ListeFichiers) {
			if (f.getNomFichier().equals(nom)){
				return f;
			}
		}
		System.out.println("Fichier "+nom+" non trouvé !");
		return null;
	}

	@Override
	public synchronized Fichier lock(String nom, int serverRepartiteurid) throws RemoteException {
		for (int i = 0; i < ListeFichiers.size(); i++) {
			Fichier f = ListeFichiers.get(i);
			if (f.getNomFichier().equals(nom)){ // Si on a le même nom de fichier, et si on arrive à locker le file c'est bon
				if (f.lockFile(serverRepartiteurid)) {
					ListeFichiers.set(i, f); // On met à jour notre fichier que l'on vient de locker au serverRepartiteur voulu
					System.out.println("Fichier "+nom+" locked par #"+serverRepartiteurid);
					return f;
				}else{
					System.out.println("Echec: le fichier "+nom+" appartient au serverRepartiteur #"+f.getServerRepartiteurId());return null;
				}
			}
		}
		System.out.println("Fichier "+nom+" non trouvé ! - #"+serverRepartiteurid);
		return null;
	}

	@Override
	public boolean push(String nom, byte[] contenu, int serverRepartiteurid) throws RemoteException {
		for (int i = 0; i < ListeFichiers.size(); i++) {
			Fichier f = ListeFichiers.get(i);
			if (f.getNomFichier().equals(nom) && f.getServerRepartiteurId() == serverRepartiteurid){
				f.setFilecontent(contenu);
				f.unlockFile();
				ListeFichiers.set(i, f); // On met à jour notre fichier que l'on vient de locker au serverRepartiteur voulu
				f.writeInFile(pathServerFolder); // on sauvegarde notre fichier sur le disque dur.
				System.out.println("Fichier "+nom+" MAJ & unlocked par #"+serverRepartiteurid);
				return true;
			}
		}
		System.out.println("Fichier "+nom+" non trouvé ! - #"+serverRepartiteurid);
		return false;
	}

	@Override
	public int generateserverRepartiteurid(int serverRepartiteurId) throws RemoteException {
		if (Integer.valueOf(serverRepartiteurId) > 0 && ListeServerRepartiteurs.contains(Integer.valueOf(serverRepartiteurId)))
			return serverRepartiteurId;
		else
			return this.generateserverRepartiteurid();
	}
	@Override
	public int generateserverRepartiteurid() {
		int id = this.getRandomNumber();
		while (ListeServerRepartiteurs.contains(id)) {
			id = this.getRandomNumber();
		}
		ListeServerRepartiteurs.add(id);
		return id;
	}
	/**
	 * Fonction pour générer un nombre aléatoire
	 * @return int random number
	 */
	private int getRandomNumber() {
		return (int) (Math.random()*100000)+1;
	}
}
