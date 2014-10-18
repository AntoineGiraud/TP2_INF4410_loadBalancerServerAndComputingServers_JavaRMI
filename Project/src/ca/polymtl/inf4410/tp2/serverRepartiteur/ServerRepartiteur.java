package ca.polymtl.inf4410.tp2.serverRepartiteur;

import java.io.File;
import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Hashtable;

import ca.polymtl.inf4410.tp2.shared.Fichier;
import ca.polymtl.inf4410.tp2.shared.ServerRepartiteurInterface;

/**
 * <p>confer {@link ServerRepartiteurInterface} pour les explications sur cette classe et ses méthodes.</p>
 * @author Antoine Giraud #1761581
 *
 */
public class ServerRepartiteur implements ServerRepartiteurInterface {

	public static void main(String[] args) {
		ServerRepartiteur server = new ServerRepartiteur();
		server.run();
	}
	
	private ArrayList<Fichier> ListeFichiers;
	private ArrayList<Integer> ListeServerCalculs;
	private String pathServerFolder;

	public ServerRepartiteur() {
		super();
		ListeFichiers = new ArrayList<Fichier>();
		ListeServerCalculs = new ArrayList<Integer>();
		pathServerFolder = "server_files";
		
		// Mettre à jour la liste des fichiers présents sur le serveur
        getAllFile(new File(pathServerFolder));
	}
	
	/**
	 * <p>Petite fonction récursive qui va nous permettre de parcourir le répertoire de notre serveur où sont situés les fichiers que l'on reçoit des serverCalculs.<br>
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
			ServerRepartiteurInterface stub = (ServerRepartiteurInterface) UnicastRemoteObject
					.exportObject(this, 5010);

			Registry registry = LocateRegistry.getRegistry("127.0.0.1", 5001);
			registry.rebind("serverRepartiteur", stub);
			System.out.println("ServerRepartiteur ready.");
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
			liste.put(f.getNomFichier(), f.getServerCalculId());
			System.out.println("* "+f.getNomFichier()+" - #"+f.getServerCalculId());
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
	public synchronized Fichier lock(String nom, int serverCalculid) throws RemoteException {
		for (int i = 0; i < ListeFichiers.size(); i++) {
			Fichier f = ListeFichiers.get(i);
			if (f.getNomFichier().equals(nom)){ // Si on a le même nom de fichier, et si on arrive à locker le file c'est bon
				if (f.lockFile(serverCalculid)) {
					ListeFichiers.set(i, f); // On met à jour notre fichier que l'on vient de locker au serverCalcul voulu
					System.out.println("Fichier "+nom+" locked par #"+serverCalculid);
					return f;
				}else{
					System.out.println("Echec: le fichier "+nom+" appartient au serverCalcul #"+f.getServerCalculId());return null;
				}
			}
		}
		System.out.println("Fichier "+nom+" non trouvé ! - #"+serverCalculid);
		return null;
	}

	@Override
	public boolean push(String nom, byte[] contenu, int serverCalculid) throws RemoteException {
		for (int i = 0; i < ListeFichiers.size(); i++) {
			Fichier f = ListeFichiers.get(i);
			if (f.getNomFichier().equals(nom) && f.getServerCalculId() == serverCalculid){
				f.setFilecontent(contenu);
				f.unlockFile();
				ListeFichiers.set(i, f); // On met à jour notre fichier que l'on vient de locker au serverCalcul voulu
				f.writeInFile(pathServerFolder); // on sauvegarde notre fichier sur le disque dur.
				System.out.println("Fichier "+nom+" MAJ & unlocked par #"+serverCalculid);
				return true;
			}
		}
		System.out.println("Fichier "+nom+" non trouvé ! - #"+serverCalculid);
		return false;
	}

	@Override
	public int generateserverCalculid(int serverCalculId) throws RemoteException {
		if (Integer.valueOf(serverCalculId) > 0 && ListeServerCalculs.contains(Integer.valueOf(serverCalculId)))
			return serverCalculId;
		else
			return this.generateserverCalculid();
	}
	@Override
	public int generateserverCalculid() {
		int id = this.getRandomNumber();
		while (ListeServerCalculs.contains(id)) {
			id = this.getRandomNumber();
		}
		ListeServerCalculs.add(id);
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
