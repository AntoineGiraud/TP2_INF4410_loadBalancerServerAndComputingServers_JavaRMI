package ca.polymtl.inf4410.tp2.serverCalcul;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
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
		final ServerCalcul server = new ServerCalcul();
		server.run();
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
		    public void run() {
		    	try {
		    		Registry registry = LocateRegistry.getRegistry(server.ipServerRMI, server.portServerRMI);
					registry.unbind("serverCalcul"+server.portEcouteStubRMI);
				} catch (AccessException e) { e.printStackTrace(); }
		    	  catch (RemoteException e) { e.printStackTrace(); }
		    	  catch (NotBoundException e) {	e.printStackTrace(); }
		    	System.out.println("Weee i'm shuting dooown");
		    }
		 });
	}
	
	private boolean malicious;
	protected int portEcouteStubRMI,
				  quantiteRessources,
				  portServerRMI = 5000;
	protected String ipServerRMI = "127.0.0.1", thisServerIp = "127.0.0.1";
	
	public ServerCalcul() {
		super();
		this.readServerPropertiesFromFile();
		this.writeServerPropertiesInFile();
		if (this.portEcouteStubRMI < 5000 || this.portEcouteStubRMI > 5050) {
			System.out.println("Attention à prendre un numero de port compris entre 5000 et 5050 pour pouvoir être éxécuté dans la salle de TP.");
		}
		System.setProperty("java.rmi.server.hostname", thisServerIp);
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

			Registry registry = LocateRegistry.getRegistry(this.ipServerRMI, this.portServerRMI);
			registry.rebind("serverCalcul"+portEcouteStubRMI, stub);
			System.out.println("ServerCalcul"+(malicious == true?" malicieux":"")+" ready at "+thisServerIp+":"+portEcouteStubRMI+", quantiteRessources:"+quantiteRessources+", ");
		} catch (ConnectException e) {
			System.err.println("Impossible de se connecter au registre RMI. Est-ce que rmiregistry est lancé ?");
			System.err.println();
			System.err.println("Erreur: " + e.getMessage());
		} catch (Exception e) {
			System.err.println("Erreur: " + e.getMessage());
		}
	}

	@Override
	public Tache compute(Tache task) throws RemoteException {
		double T = (double)(task.getNbOperations()-quantiteRessources)/(9*quantiteRessources);
		double rand = Math.random();
		if (T <= 0 || rand > T) {
			System.out.println("Acceptation tâche "+task.getID()+" -- taux de refus: "+(double)(((double)Math.round(T*100))/100));
			task.compute();
		}else{
			System.out.println("Refus       tâche "+task.getID()+" -- taux de refus: "+(double)(((double)Math.round(T*100))/100));
			task.setResultat(null);
			task.setToRefusedState();
		}
		return task;
	}
	
	/**
	 * <p>Fonction pour lire l'ID que l'on s'est vu attribuer par le serveur<br>
	 * On utilise la classe {@link Properties} pour nous aider à gérer plus facilement notre fichier de conf...</p>
	 */
	private void readServerPropertiesFromFile() {
		Properties properties = new Properties();
		File f = new File("ServerCalcul.properties");
        if (!f.exists()) { try { 
        	f.createNewFile(); // Si le fichier n'existait pas on le créé
        	this.portEcouteStubRMI = 0;
        	this.quantiteRessources = 5;
        	this.malicious = false;
        	this.ipServerRMI = "127.0.0.1";
        	this.thisServerIp = "127.0.0.1";
        	this.portServerRMI = 5000;
        	
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
	        	this.portEcouteStubRMI = properties.getProperty("portEcouteStubRMI") == null ? 0 : Integer.valueOf(properties.getProperty("portEcouteStubRMI"));
	        	this.quantiteRessources = properties.getProperty("quantiteRessources") == null ? 5 : Integer.valueOf(properties.getProperty("quantiteRessources"));
	        	this.malicious = properties.getProperty("malicious") == null ? false : Boolean.valueOf(properties.getProperty("malicious"));
	        	this.portServerRMI = properties.getProperty("portServerRMI") == null ? 5000 : Integer.valueOf(properties.getProperty("portServerRMI"));
	        	this.ipServerRMI = properties.getProperty("ipServerRMI") == null ? "127.0.0.1" : properties.getProperty("ipServerRMI");
	        	this.thisServerIp = properties.getProperty("thisServerIp") == null ? "127.0.0.1" : properties.getProperty("thisServerIp");
	        }else{
	        	this.portEcouteStubRMI = 0;
	        	this.quantiteRessources = 5;
	        	this.malicious = false;
	        	this.ipServerRMI = "127.0.0.1";
	        	this.thisServerIp = "127.0.0.1";
	        	this.portServerRMI = 5000;
	        }
        }
	}
	/**
	 * <p>Fonction pour écrire l'ID que l'on s'est vu attribuer par le serveur<br>
	 * On utilise la classe {@link Properties} pour nous aider à gérer plus facilement notre fichier de conf...</p>
	 */
	private void writeServerPropertiesInFile() {
        Properties properties = new Properties();
        properties.setProperty("portEcouteStubRMI", Integer.toString(this.portEcouteStubRMI));
        properties.setProperty("quantiteRessources", Integer.toString(this.quantiteRessources));
        properties.setProperty("malicious", Boolean.toString(this.malicious));
        properties.setProperty("ipServerRMI", this.ipServerRMI);
        properties.setProperty("thisServerIp", this.thisServerIp);
        properties.setProperty("portServerRMI", Integer.toString(this.portServerRMI));

        //Store in the properties file
        File f = new File("ServerCalcul.properties");
        try {
			FileOutputStream fileOutputStream = new FileOutputStream(f);
			properties.store(fileOutputStream, null);
			fileOutputStream.close();
		} catch (FileNotFoundException e) { e.printStackTrace(); }
          catch (IOException e) { e.printStackTrace(); }
	}

	@Override
	public boolean ping() throws RemoteException {
		return true;
	}
}