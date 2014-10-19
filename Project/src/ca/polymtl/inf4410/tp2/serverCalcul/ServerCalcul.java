package ca.polymtl.inf4410.tp2.serverCalcul;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Properties;

import ca.polymtl.inf4410.tp2.shared.ServerCalculInterface;
import ca.polymtl.inf4410.tp2.shared.Tache;

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
	
	public String serverCalculName;
	private int portEcouteStubRMI;
	
	public ServerCalcul() {
		super();
		this.readServerRepartiteurIdFromFile();
		this.writeServerRepartiteurIdInFile();
		if (this.portEcouteStubRMI < 5000 || this.portEcouteStubRMI > 5050) {
			System.out.println("Attention à prendre un numero de port compris entre 5000 et 5050 pour pouvoir être éxécuté dans la salle de TP.");
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
					.exportObject(this, this.portEcouteStubRMI);

			Registry registry = LocateRegistry.getRegistry("127.0.0.1", 5000);
			registry.rebind("serverCalcul"+portEcouteStubRMI, stub);
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
	public int compute(Tache task) throws RemoteException {
		return task.compute();
	}
	
	/**
	 * <p>Fonction pour lire l'ID que l'on s'est vu attribuer par le serveur<br>
	 * On utilise la classe {@link Properties} pour nous aider à gérer plus facilement notre fichier de conf...</p>
	 */
	private void readServerRepartiteurIdFromFile() {
		Properties properties = new Properties();
		File f = new File("ServerCalcul.properties");
        if (!f.exists()) { try { 
        	f.createNewFile(); // Si le fichier n'existait pas on le créé
        	this.portEcouteStubRMI = 0; // On a alors pas d'ID déjà donc on fixe 0
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
	        	this.portEcouteStubRMI = properties.isEmpty() ? 0 : Integer.valueOf(properties.getProperty("portEcouteStubRMI"));
	        }else{
	        	this.portEcouteStubRMI = 0;
	        }
        }
	}
	/**
	 * <p>Fonction pour écrire l'ID que l'on s'est vu attribuer par le serveur<br>
	 * On utilise la classe {@link Properties} pour nous aider à gérer plus facilement notre fichier de conf...</p>
	 */
	private void writeServerRepartiteurIdInFile() {
        Properties properties = new Properties();
        properties.setProperty("portEcouteStubRMI", Integer.toString(this.portEcouteStubRMI));

        //Store in the properties file
        File f = new File("ServerCalcul.properties");
        try {
			FileOutputStream fileOutputStream = new FileOutputStream(f);
			properties.store(fileOutputStream, null);
			fileOutputStream.close();
		} catch (FileNotFoundException e) { e.printStackTrace(); }
          catch (IOException e) { e.printStackTrace(); }
	}
}