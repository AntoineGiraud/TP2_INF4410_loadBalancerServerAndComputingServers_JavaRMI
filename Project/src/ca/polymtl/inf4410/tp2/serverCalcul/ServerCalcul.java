package ca.polymtl.inf4410.tp2.serverCalcul;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import ca.polymtl.inf4410.tp2.shared.Fichier;
import ca.polymtl.inf4410.tp2.shared.ServerRepartiteurInterface;

/**
 * <p>
 * Le serverCalcul exposera les commandes list et get pour consulter les fichiers du serveur.<br>
 * La commande create permettra de créer un nouveau fichier.<br>
 * Pour modifier un fichier, l'utilisateur devra d'abord le verrouiller à l'aide de la commande lock.<br>
 * Il appliquera ensuite ses modifications au fichier local et les publiera sur le serveur à l'aide la commande push.<br>
 * Notons qu'un seul serverCalcul peut verrouiller un fichier donné à la fois.<br>
 * Aussi, la commande lock téléchargera toujours la dernière version du fichier afin d'éviter que l'utilisateur n'applique ses modifications à une version périmée.<br>
 * </p>
 * @author Antoine Giraud #1761581
 *
 */
public class ServerCalcul {
	public static void main(String[] args) {
		ServerCalcul serverCalcul = new ServerCalcul();
		if (args.length > 0) {
			functions f = functions.valueOf(args[0]);
			switch (f){
				case list: serverCalcul.list(); break;
				case create: serverCalcul.create(args[1]); break;
				case get: serverCalcul.get(args[1]); break;
				case push: serverCalcul.push(args[1]); break;
				case lock: serverCalcul.lock(args[1]); break;
				default: System.out.println("Mauvaise command");
			}
		}else
			System.out.println("entrer une command comme argument");
	}

	private int serverCalculId;
	private ServerRepartiteurInterface localServerStub = null;
	private enum functions { list, create, get, push, lock};

	public ServerCalcul() {
		super();
		if (System.getSecurityManager() == null) {
            System.setSecurityManager(new SecurityManager());
        }

		localServerStub = loadServerStub("127.0.0.1",5001); // On est en local
		
		// On s'authentifie au serveur. On récupère l'ID de notre ServerCalcul que l'on store dans un fichier.
		this.readServerCalculIdFromFile();
		try {
			this.serverCalculId = localServerStub.generateserverCalculid(this.serverCalculId);
			System.out.println("Votre ID serverCalcul :"+this.serverCalculId);
			this.writeServerCalculIdInFile();
		} catch (RemoteException e) { e.printStackTrace(); }
	}	
	
	/**
	 * <p>La commande lock permet de vérouiller un fichier pour s'assurer que l'on peut le modifier sans que qqn d'autre puisse mettre à jour le fichier sur le serveur.<br>
	 * On vérifie au cas échéant pourquoi l'on a pas pu vérouiller le fichier en le récupérant à nouveau avec la commande get. </p>
	 * @param nom String Nom du fichier à blocker
	 */
	private void lock(String nom) {
		try {
			Fichier file = localServerStub.lock(nom, this.serverCalculId);
			if (file == null) {
				Fichier f = localServerStub.get(nom);
				if (f == null) {
					System.out.println("Echec: le fichier "+nom+" n'existe pas");
				}else{
					System.out.println("Echec: le fichier "+nom+" appartient au serverCalcul #"+f.getServerCalculId());
				}
			}else{
				file.writeInFile(); // on sauvegarde notre fichier sur le disque dur.
				System.out.println(file.getNomFichier()+" a bien été vérouillé et récupéré. Vous pouvez désormais l'éditer.");
			}
		} catch (RemoteException e) { System.out.println("Erreur: " + e.getMessage()); }
	}
	
	/**
	 * <p>Commande pour demander au serveur de mettre à jour le fichier que l'on avait au préalablement vérouillé<br>
	 * L'opération échoue si le fichier n'avait pas été verrouillé par le serverCalcul préalablement.<br>
	 * On récupère avant côté serverCalcul, en tampon, le fichier que l'on veut mettre à jour pour s'assurer que le fichier :
	 * <ul><li>il existe</li><li>est vérouillé</li><li>est vérouillé par nous</li></ul>
	 * </p>
	 * @param nom String Nom du fichier à mettre à jour
	 */
	private void push(String nom) {
		try {
			Fichier f = localServerStub.get(nom);
			if (f == null) {
				System.out.println("Echec: le fichier "+nom+" n'existe pas");
			}else if(f.getServerCalculId() == 0){
				System.out.println("Echec: Vous devez vérouiller (lock) votre fichier pour pouvoir faire un push");
			}else if(f.getServerCalculId() > 0 && f.getServerCalculId() != this.serverCalculId){
				System.out.println("Echec: le fichier "+nom+" appartient au serverCalcul #"+f.getServerCalculId());
			}else{
				Fichier file = new Fichier(nom);
				try {
					if(localServerStub.push(nom, file.getFilecontent(), this.serverCalculId))
						System.out.println("Le fichier "+nom+" a bien été mis à jour");
					else{
						System.out.println("Erreur imprévue");
					}
				} catch (RemoteException e) { System.out.println("Erreur: " + e.getMessage()); }
			}
		} catch (RemoteException e) { System.out.println("Erreur: " + e.getMessage()); }
	}
	
	/**
	 * <p>la commande <strong>get</strong> permet au serverCalcul de récupérer un fichier par son nom sur son disque, pour le peu qu'il existe.<br>
	 * Lorsque le serverCalcul met à jour un fichier qu'il possédait déjà (get), le nouveau contenu écrase simplement l'ancien.</p>
	 * @param nom String : nom du fichier que l'on cherche à récupérer
	 */
	private void get(String nom) {
		try {
			Fichier file = localServerStub.get(nom);
			if (file == null) {
				System.out.println("Echec: le fichier "+nom+" n'existe pas");
			}else{
				file.writeInFile(); // on sauvegarde notre fichier sur le disque dur.
				System.out.println(file.getNomFichier()+" a bien été récupéré");
			}
		} catch (RemoteException e) { System.out.println("Erreur: " + e.getMessage()); }
	}
	
	/**
	 * <p>Commande create pour créer un nouveau fichier.<br>
	 * Soit le fichier n'existe pas alors il est créé, soit il existe rien ne se passe, on est averti de l'erreur.<br>
	 * Rmq: On créé le fichier sur le serveur et on en profite pour faire un get dans la foulée pour avoir ce nouveau fichier sur notre ordinateur.
	 * </p>
	 * @param nom String : nom du fichier que l'on cherche à créer
	 */
	private void create(String nom) {
		try {
			if(localServerStub.create(nom)){
				this.get(nom);
				System.out.println("Le fichier "+nom+" a bien été ajouté");	
			}else
				System.out.println("Echec: le fichier "+nom+" existe déjà");
		} catch (RemoteException e) { System.out.println("Erreur: " + e.getMessage()); }
	}
	
	/**
	 * Commande pour afficher la liste des fichiers du serveur.
	 */
	private void list() {
		try {
			Hashtable<String, Integer> fileList = localServerStub.list();
			if (fileList.isEmpty()) {
				System.out.println("0 fichiers");
			}else{
				System.out.println(fileList.size()+" fichier"+(fileList.size()>1?"s":"")+" :");
				
				// On récupère les clés de notre Hashtable, id est les fileNames
				Set<String> set = fileList.keySet();
				String fileName;
			    Iterator<String> fileNames = set.iterator();
			    while (fileNames.hasNext()) {
			    	fileName = fileNames.next();
			      System.out.println("* "+fileName + " - #" + fileList.get(fileName));
			    }
			}
		} catch (RemoteException e) { System.out.println("Erreur: " + e.getMessage()); }
	}
	
	/**
	 * Fonction pour se connecter à notre serveur RMI.
	 * @param hostname IP du registre RMI à accéder.
	 * @return {@link ServerRepartiteurInterface}
	 */
	private ServerRepartiteurInterface loadServerStub(String hostname) {
		ServerRepartiteurInterface stub = null;
		try {
			Registry registry = LocateRegistry.getRegistry(hostname);
			stub = (ServerRepartiteurInterface) registry.lookup("serverRepartiteur");
		} catch (NotBoundException e) {
			System.out.println("Erreur: Le nom '" + e.getMessage()
					+ "' n'est pas défini dans le registre.");
		} catch (AccessException e) {
			System.out.println("Erreur: " + e.getMessage());
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		}
		return stub;
	}
	private ServerRepartiteurInterface loadServerStub(String hostname, int port) {
		ServerRepartiteurInterface stub = null;
		try {
			Registry registry = LocateRegistry.getRegistry(hostname, port);
			stub = (ServerRepartiteurInterface) registry.lookup("serverRepartiteur");
		} catch (NotBoundException e) {
			System.out.println("Erreur: Le nom '" + e.getMessage()
					+ "' n'est pas défini dans le registre.");
		} catch (AccessException e) {
			System.out.println("Erreur: " + e.getMessage());
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		}
		return stub;
	}
	
	/**
	 * <p>Fonction pour lire l'ID que l'on s'est vu attribuer par le serveur<br>
	 * On utilise la classe {@link Properties} pour nous aider à gérer plus facilement notre fichier de conf...</p>
	 */
	private void readServerCalculIdFromFile() {
		Properties properties = new Properties();
		File f = new File("serverCalcul.properties");
        if (!f.exists()) { try { 
        	f.createNewFile(); // Si le fichier n'existait pas on le créé
        	this.serverCalculId = 0; // On a alors pas d'ID déjà donc on fixe 0
        } catch (IOException e1) { e1.printStackTrace();}}
        else{
	        FileInputStream fileInputStream = null;
	        
	        //Ouverture & lecture du fichier
			try {
				fileInputStream = new FileInputStream(f);
				properties.load(fileInputStream); // On charge les propriétés stockées dans notre fichier
				fileInputStream.close();
			} catch (IOException e) { e.printStackTrace(); }
	        if (fileInputStream != null) { // Si on a réussi à ouvrir le fichier
	        	this.serverCalculId = properties.isEmpty() ? 0 : Integer.valueOf(properties.getProperty("ServerCalculID"));
	        }else{
	        	this.serverCalculId = 0;
	        }
        }
	}
	/**
	 * <p>Fonction pour écrire l'ID que l'on s'est vu attribuer par le serveur<br>
	 * On utilise la classe {@link Properties} pour nous aider à gérer plus facilement notre fichier de conf...</p>
	 */
	private void writeServerCalculIdInFile() {
        Properties properties = new Properties();
        properties.setProperty("ServerCalculID", Integer.toString(this.serverCalculId));

        //Store in the properties file
        File f = new File("serverCalcul.properties");
        try {
			FileOutputStream fileOutputStream = new FileOutputStream(f);
			properties.store(fileOutputStream, null);
			fileOutputStream.close();
		} catch (FileNotFoundException e) { e.printStackTrace(); }
          catch (IOException e) { e.printStackTrace(); }
	}
}
