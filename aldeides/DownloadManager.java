package aldeides;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

public class DownloadManager extends JFrame implements Observer {

	InputStream is;
	Pattern p = Pattern.compile("(attachmentid=)(\\w*)");
	Matcher m;
	HttpURLConnection conn;

	// Add download text field.
	private JTextField addTextField;

	// Download table's data model.
	private DownloadsTableModel tableModel;

	// Table listing downloads.
	private JTable table;

	// These are the buttons for managing the selected download.
	private JButton pauseButton, resumeButton;
	private JButton cancelButton, clearButton;

	// Currently selected download.
	private Download selectedDownload;

	// Flag for whether or not table selection is being cleared.
	private boolean clearing;

	public DownloadManager() {
		// Set application title.
		setTitle("Download Manager");

		// Set window size.
		setSize(640, 480);

		// Handle window closing events.
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				actionExit();
			}
		});

		// Set up file menu.
		JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		fileMenu.setMnemonic(KeyEvent.VK_F);
		JMenuItem fileExitMenuItem = new JMenuItem("Exit", KeyEvent.VK_X);
		fileExitMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionExit();
			}
		});

		fileMenu.add(fileExitMenuItem);
		menuBar.add(fileMenu);
		setJMenuBar(menuBar);

		// Set up add panel.
		JPanel addPanel = new JPanel();
		addTextField = new JTextField(30);
		addPanel.add(addTextField);
		JButton addButton = new JButton("Add Download");
		addButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionAdd();
			}
		});
		addPanel.add(addButton);

		// Set up Downloads table.
		tableModel = new DownloadsTableModel();
		table = new JTable(tableModel);
		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				tableSelectionChanged();
			}
		});

		// Allow only one row at a time to be selected.
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		// Set up ProgressBar as renderer for progress column.
		ProgressRenderer renderer = new ProgressRenderer(0, 100);
		renderer.setStringPainted(true); // show progress text
		table.setDefaultRenderer(JProgressBar.class, renderer);

		// Set table's row height large enough to fit JProgressBar.
		table.setRowHeight((int) renderer.getPreferredSize().getHeight());

		// Set up downloads panel.
		JPanel downloadsPanel = new JPanel();
		downloadsPanel.setBorder(BorderFactory.createTitledBorder("Downloads"));
		downloadsPanel.setLayout(new BorderLayout());
		downloadsPanel.add(new JScrollPane(table), BorderLayout.CENTER);

		// Set up buttons panel.
		JPanel buttonsPanel = new JPanel();
		pauseButton = new JButton("Pause");
		pauseButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionPause();
			}
		});
		pauseButton.setEnabled(false);
		buttonsPanel.add(pauseButton);
		resumeButton = new JButton("Resume");
		resumeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionResume();
			}
		});
		resumeButton.setEnabled(false);
		buttonsPanel.add(resumeButton);
		cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionCancel();
			}
		});
		cancelButton.setEnabled(false);
		buttonsPanel.add(cancelButton);
		clearButton = new JButton("Clear");
		clearButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionClear();
			}
		});
		clearButton.setEnabled(false);
		buttonsPanel.add(clearButton);

		// Add panels to display.
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(addPanel, BorderLayout.NORTH);
		getContentPane().add(downloadsPanel, BorderLayout.CENTER);
		getContentPane().add(buttonsPanel, BorderLayout.SOUTH);
	}

	private void actionAdd() {
		ArrayList<String> linkList = new ArrayList();
		String url = addTextField.getText();
		String dir = "Dir";
		try {
			conn = (HttpURLConnection) (new URL(url)).openConnection();
			HttpURLConnection.setFollowRedirects(true);
			conn.setRequestProperty("User-Agent", "my agent name");
			String encoding = conn.getContentEncoding();
			if (encoding != null && encoding.equalsIgnoreCase("gzip!")) {
				is = new GZIPInputStream(conn.getInputStream());
			} else if (encoding != null && encoding.equalsIgnoreCase("deflate")) {
				is = new InflaterInputStream(conn.getInputStream());
			} else {
				//TODO: Problema con la directory
				//dir = createDir(url);
				// System.out.println("Directory: "+dir);
				linkList = extractUrlsFromString(url);
				if (!linkList.isEmpty())
					System.out.println("url creati");
			}

			for (String s : linkList) {
				System.out.println(s);
				tableModel.addDownload(new Download(new URL(s), dir));
				addTextField.setText(""); // reset add text field

			}
		} catch (Exception e) {
			e.printStackTrace();
			Logger.getLogger(DownloadManager.class.getName()).log(Level.SEVERE, "Url non ben formattata", e);
		}
	}

	private String createDir(String url) {
		Pattern p = Pattern.compile("<title>(.+?) - Allegati</title>");
		Matcher m;
		String line, updated = "Dir";

		try {
			is = conn.getInputStream();
			BufferedReader in = new BufferedReader(new InputStreamReader(is));
			while ((line = in.readLine()) != null) {
				m = p.matcher(line);
				while (m.find()) {
					updated = m.group(1);
				}
			}
			// updated=java.net.URLEncoder.encode(updated,"UTF-8");
			updated = updated.replaceAll("[-+.^:,!?]", "");
			boolean dir = new File(updated).mkdir();

		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		return updated;
	}

	public ArrayList<String> extractUrlsFromString(String content) {
		ArrayList<String> result = new ArrayList<String>();
		try {
			is = conn.getInputStream();
			if (is == null)
				System.err.println("ERRORE!!!");
			BufferedReader in = new BufferedReader(new InputStreamReader(is));
			if (in == null)
				System.err.println("ERRORE BuffReader!!!");
			String line;
			while ((line = in.readLine()) != null) {
				m = p.matcher(line);
				while (m.find())
					result.add("http://www.phica.net/forums/attachment.php?" + m.group());
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
			System.out.println("Problemi con la connessione");
		}

		System.out.println(result);
		return result;
	}

	// Verify download URL.
	private URL verifyUrl(String url) {
		// Only allow HTTP URLs.
		if (!url.toLowerCase().startsWith("http://"))
			return null;

		// Verify format of URL.
		URL verifiedUrl = null;
		try {
			verifiedUrl = new URL(url);
		} catch (Exception e) {
			return null;
		}

		// Make sure URL specifies a file.
		if (verifiedUrl.getFile().length() < 2)
			return null;

		return verifiedUrl;
	}

	// Called when table row selection changes.
	private void tableSelectionChanged() {
		/*
		 * Unregister from receiving notifications from the last selected download.
		 */
		if (selectedDownload != null)
			selectedDownload.deleteObserver(DownloadManager.this);

		/*
		 * If not in the middle of clearing a download, set the selected download and
		 * register to receive notifications from it.
		 */
		if (!clearing) {
			selectedDownload = tableModel.getDownload(table.getSelectedRow());
			selectedDownload.addObserver(DownloadManager.this);
			updateButtons();
		}
	}

	// Pause the selected download.
	private void actionPause() {
		selectedDownload.pause();
		updateButtons();
	}

	// Resume the selected download.
	private void actionResume() {
		selectedDownload.resume();
		updateButtons();
	}

	// Cancel the selected download.
	private void actionCancel() {
		selectedDownload.cancel();
		updateButtons();
	}

	// Clear the selected download.
	private void actionClear() {
		clearing = true;
		tableModel.clearDownload(table.getSelectedRow());
		clearing = false;
		selectedDownload = null;
		updateButtons();
	}

	// Exit this program.
	private void actionExit() {
		System.exit(0);
	}

	/*
	 * Update each button's state based off of the currently selected download's
	 * status.
	 */
	private void updateButtons() {
		if (selectedDownload != null) {
			int status = selectedDownload.getStatus();
			switch (status) {
			case Download.DOWNLOADING:
				pauseButton.setEnabled(true);
				resumeButton.setEnabled(false);
				cancelButton.setEnabled(true);
				clearButton.setEnabled(false);
				break;
			case Download.PAUSED:
				pauseButton.setEnabled(false);
				resumeButton.setEnabled(true);
				cancelButton.setEnabled(true);
				clearButton.setEnabled(false);
				break;
			case Download.ERROR:
				pauseButton.setEnabled(false);
				resumeButton.setEnabled(true);
				cancelButton.setEnabled(false);
				clearButton.setEnabled(true);
				break;
			default: // COMPLETE or CANCELLED
				pauseButton.setEnabled(false);
				resumeButton.setEnabled(false);
				cancelButton.setEnabled(false);
				clearButton.setEnabled(true);
			}
		} else {
			// No download is selected in table.
			pauseButton.setEnabled(false);
			resumeButton.setEnabled(false);
			cancelButton.setEnabled(false);
			clearButton.setEnabled(false);
		}
	}

	/*
	 * Update is called when a Download notifies its observers of any changes.
	 */
	public void update(Observable o, Object arg) {
		// Update buttons if the selected download has changed.
		if (selectedDownload != null && selectedDownload.equals(o))
			updateButtons();
	}

	public static void main(String[] args) {
		DownloadManager manager = new DownloadManager();
		manager.show();
	}
}
