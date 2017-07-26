/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openslide.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Container;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.Toolkit;
import java.awt.geom.Rectangle2D;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import javax.swing.table.*;
import javax.swing.filechooser.FileSystemView;

import javax.imageio.ImageIO;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import java.io.*;
import java.nio.channels.FileChannel;

import java.net.URL;
import java.util.Comparator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import static javax.swing.Action.ACCELERATOR_KEY;
import static javax.swing.Action.NAME;
import static javax.swing.Action.SHORT_DESCRIPTION;
import org.openslide.AssociatedImage;
import org.openslide.OpenSlide;

import java.io.FileWriter;
 
import com.opencsv.CSVWriter;
 
/**
A basic File Browser.  Requires 1.6+ for the Desktop & SwingWorker
classes, amongst other minor things.

Includes support classes FileTableModel & FileTreeCellRenderer.

@TODO Bugs
<li>Fix keyboard focus issues - especially when functions like
rename/delete etc. are called that update nodes & file lists.
<li>Needs more testing in general.

@TODO Functionality
<li>Double clicking a directory in the table, should update the tree
<li>Move progress bar?
<li>Add other file display modes (besides table) in CardLayout?
<li>Menus + other cruft?
<li>Implement history/back
<li>Allow multiple selection
<li>Add file search

@author Andrew Thompson
@version 2011-06-08
@see http://stackoverflow.com/questions/6182110
@license LGPL
*/
class FileBrowser {

    /** Title of the application */
    public static final String APP_TITLE = "FileBro";
    /** Used to open/edit/print files. */
    private Desktop desktop;
    /** Provides nice icons and names for files. */
    private FileSystemView fileSystemView;

    /** currently selected File. */
    private File currentFile;

    /** Main GUI container */
    private JPanel gui;

    /** File-system tree. Built Lazily */
    private JTree tree;
    private DefaultTreeModel treeModel;

    /** Directory listing */
    private JTable table;
    private JProgressBar progressBar;
    /** Table model for File[]. */
    private FileTableModel fileTableModel;
    private ListSelectionListener listSelectionListener;
    private boolean cellSizesSet = false;
    private int rowIconPadding = 6;

    /* File controls. */
    private JButton openFile;
    private JButton showThumbnail;
    private JButton showProperties;
    private JButton selection;
    private JButton locateFile;

    /* File details. */
    private JLabel fileName;
    private JTextField path;
    private JLabel date;
    private JLabel size;
    private JCheckBox readable;
    private JCheckBox writable;
    private JCheckBox executable;
    private JRadioButton isDirectory;
    private JRadioButton isFile;

    /* GUI options/containers for new File/Directory creation.  Created lazily. */
    private JPanel newFilePanel;
    private JRadioButton newTypeFile;
    private JTextField name;
    
    class Action extends AbstractAction {
        public Action(String text, Icon icon, String description,
            char accelerator) {
          super(text, icon);
          putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(accelerator,
              Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
          putValue(SHORT_DESCRIPTION, description);
        }

        public void actionPerformed(ActionEvent e) {
          try {            
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        }
    }

    public Container getGui() {
        if (gui==null) {
            gui = new JPanel(new BorderLayout(3,3));
            gui.setBorder(new EmptyBorder(5,5,5,5));

            fileSystemView = FileSystemView.getFileSystemView();
            desktop = Desktop.getDesktop();

            JPanel detailView = new JPanel(new BorderLayout(3,3));

            table = new JTable();
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setAutoCreateRowSorter(true);
            table.setShowVerticalLines(false);

            listSelectionListener = new ListSelectionListener() {
                @Override
                public void valueChanged(ListSelectionEvent lse) {
                    int row = table.getSelectionModel().getLeadSelectionIndex();
                    setFileDetails( ((FileTableModel)table.getModel()).getFile(row) );
                }
            };
            table.getSelectionModel().addListSelectionListener(listSelectionListener);
            JScrollPane tableScroll = new JScrollPane(table);
            Dimension d = tableScroll.getPreferredSize();
            tableScroll.setPreferredSize(new Dimension((int)d.getWidth(), (int)d.getHeight()/2));
            detailView.add(tableScroll, BorderLayout.CENTER);

            // the File tree
            DefaultMutableTreeNode root = new DefaultMutableTreeNode();
            treeModel = new DefaultTreeModel(root);

            TreeSelectionListener treeSelectionListener = new TreeSelectionListener() {
                public void valueChanged(TreeSelectionEvent tse){
                    DefaultMutableTreeNode node =
                        (DefaultMutableTreeNode)tse.getPath().getLastPathComponent();
                    showChildren(node);
                    setFileDetails((File)node.getUserObject());
                }
            };

            // show the file system roots.
            File[] roots = fileSystemView.getRoots();
            for (File fileSystemRoot : roots) {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(fileSystemRoot);
                root.add( node );
                File[] files = fileSystemView.getFiles(fileSystemRoot, true);
                for (File file : files) {
                    if (file.isDirectory()) {
                        node.add(new DefaultMutableTreeNode(file));
                    }
                }
                //
            }

            tree = new JTree(treeModel);
            tree.setRootVisible(false);
            tree.addTreeSelectionListener(treeSelectionListener);
            tree.setCellRenderer(new FileTreeCellRenderer());
            tree.expandRow(0);
            JScrollPane treeScroll = new JScrollPane(tree);

            // as per trashgod tip
            tree.setVisibleRowCount(15);

            Dimension preferredSize = treeScroll.getPreferredSize();
            Dimension widePreferred = new Dimension(
                200,
                (int)preferredSize.getHeight());
            treeScroll.setPreferredSize( widePreferred );

            // details for a File
            JPanel fileMainDetails = new JPanel(new BorderLayout(4,2));
            fileMainDetails.setBorder(new EmptyBorder(0,6,0,6));

            JPanel fileDetailsLabels = new JPanel(new GridLayout(0,1,2,2));
            fileMainDetails.add(fileDetailsLabels, BorderLayout.WEST);

            JPanel fileDetailsValues = new JPanel(new GridLayout(0,1,2,2));
            fileMainDetails.add(fileDetailsValues, BorderLayout.CENTER);

            fileDetailsLabels.add(new JLabel("File", JLabel.TRAILING));
            fileName = new JLabel();
            fileDetailsValues.add(fileName);
            fileDetailsLabels.add(new JLabel("Path/name", JLabel.TRAILING));
            path = new JTextField(5);
            path.setEditable(false);
            fileDetailsValues.add(path);
            fileDetailsLabels.add(new JLabel("Last Modified", JLabel.TRAILING));
            date = new JLabel();
            fileDetailsValues.add(date);
            fileDetailsLabels.add(new JLabel("File size", JLabel.TRAILING));
            size = new JLabel();
            fileDetailsValues.add(size);
            fileDetailsLabels.add(new JLabel("Type", JLabel.TRAILING));

            JPanel flags = new JPanel(new FlowLayout(FlowLayout.LEADING,4,0));

            isDirectory = new JRadioButton("Directory");
            flags.add(isDirectory);

            isFile = new JRadioButton("File");
            flags.add(isFile);
            fileDetailsValues.add(flags);

            JToolBar toolBar = new JToolBar();
            // mnemonics stop working in a floated toolbar
            toolBar.setFloatable(false);

            
            openFile = new JButton("Open Slide");
            openFile.setMnemonic('o');

            openFile.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent ae) {
                    try {
                        
                        // System.out.println("Open: " + currentFile);
                        //desktop.open(currentFile);
                        // openOne(currentFile, theJf);
                        
                        JFrame jf = new JFrame("OpenSlide");
                        // jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                        
                        
                        OpenSlide os;
                        os = new OpenSlide(currentFile);
                        final OpenSlideView wv = new OpenSlideView(os, true);
                        wv.setBorder(BorderFactory.createTitledBorder(currentFile.getName()));
                        jf.getContentPane().add(wv);

                        
                        JToolBar openSlideToolBar = new JToolBar("OpenSlide ToolBar");
                        openSlideToolBar.setFloatable(false);
                          
                        
                        JList annotationList = new JList(wv.getSelectionListModel());
                        JScrollPane listPane = new JScrollPane(annotationList);

                        jf.add(listPane, BorderLayout.EAST);

                        
                        JSplitPane splitPane = new JSplitPane(
                            JSplitPane.HORIZONTAL_SPLIT,
                            wv,
                            listPane);
                        
                        jf.add(splitPane, BorderLayout.CENTER);
                        
                        splitPane.setVisible(true);  
                        
                        
                        
                        
                        
                        
                        
                        
                        
                        // JRadioButton  viewingAnnotationButton = new JRadioButton (new ImageIcon("magnifier.png"));
                        // JRadioButton  rectangularAnnotationButton = new JRadioButton (new ImageIcon("rectangle_red.png"));
                        // JRadioButton  freehandAnnotationButton = new JRadioButton (new ImageIcon("polygon_red.png"));
                        
                        JRadioButton  viewingAnnotationButton = new JRadioButton (new ImageIcon("magnifier.png"), true);
                        JRadioButton  rectangularAnnotationButton = new JRadioButton (new ImageIcon("rectangle_red.png"), false);
                        JRadioButton  ellipseAnnotationButton = new JRadioButton (new ImageIcon("ellipse_red.png"), false);
                        JRadioButton  freehandAnnotationButton = new JRadioButton (new ImageIcon("polygon_red.png"), false);
                        
                        viewingAnnotationButton.setSelectedIcon(new ImageIcon("magnifier_selected.png"));
                        rectangularAnnotationButton.setSelectedIcon(new ImageIcon("rectangle_red_selected.png"));
                        ellipseAnnotationButton.setSelectedIcon(new ImageIcon("ellipse_red_selected.png"));
                        freehandAnnotationButton.setSelectedIcon(new ImageIcon("polygon_red_selected.png"));
                        
                        ButtonGroup annotationGroup = new ButtonGroup();
                        annotationGroup.add(viewingAnnotationButton);
                        annotationGroup.add(rectangularAnnotationButton);
                        annotationGroup.add(ellipseAnnotationButton);
                        annotationGroup.add(freehandAnnotationButton);
      
                        JButton selectionDeleteButton = new JButton(new ImageIcon("delete_red.png"));
                        JButton selectionSaveButton = new JButton(new ImageIcon("floppy_35inch_red.png"));
                        JCheckBox selectionPanelButton = new JCheckBox(new ImageIcon("file_yellow.png"));

                        selectionPanelButton.setSelectedIcon(new ImageIcon("file_yellow_selected.png"));
                        selectionPanelButton.setSelected(false);
                        
                        wv.setSelectionMode(OpenSlideView.SelectionMode.NONE);                         
                                
                        //Add action listener to button
                        viewingAnnotationButton.addActionListener(new ActionListener() { 
                            public void actionPerformed(ActionEvent e)
                            {
                                wv.setSelectionMode(OpenSlideView.SelectionMode.NONE);
                            }
                        });   
        
                        //Add action listener to button
                        rectangularAnnotationButton.addActionListener(new ActionListener() { 
                            public void actionPerformed(ActionEvent e)
                            {
                                wv.setSelectionMode(OpenSlideView.SelectionMode.RECT);
                            }
                        });   
                        
                        //Add action listener to button
                        freehandAnnotationButton.addActionListener(new ActionListener() { 
                            public void actionPerformed(ActionEvent e)
                            {
                                wv.setSelectionMode(OpenSlideView.SelectionMode.FREEHAND);
                            }
                        });   
                        
                        //Add action listener to button
                        selectionSaveButton.addActionListener(new ActionListener() { 
                            public void actionPerformed(ActionEvent e)
                            {
                                // do save
                                try {
                                        CSVWriter writer = new CSVWriter(new FileWriter(currentFile.getCanonicalPath()+".osa"));
                                        
                                        for(int i = 0; i < wv.getSelectionListModel().getSize(); i ++) {
                                            Annotation a = wv.getSelectionListModel().get(i);
                                            Rectangle2D rect = (Rectangle)(a.getShape()).getBounds2D();

                                            String [] record = new String[4];
                                            record[0] = Integer.toString((int)rect.getX());
                                            record[1] = Integer.toString((int)rect.getY());
                                            record[2] = Integer.toString((int)rect.getWidth());
                                            record[3] = Integer.toString((int)rect.getHeight());

                                            writer.writeNext(record);
                                        }
                                        writer.close();
                                }
                                catch(Exception e2) {}
                            }
                        });                          
                        
                        selectionDeleteButton.addActionListener(new ActionListener() { 
                            public void actionPerformed(ActionEvent e)
                            {
                                wv.getSelectionListModel().remove(annotationList.getSelectedIndex());
                                wv.repaint();
                            }
                        });                          
                                
                                
                        selectionPanelButton.addActionListener(new ActionListener() { 
                            public void actionPerformed(ActionEvent e)
                            {
                                AbstractButton abstractButton = (AbstractButton) e.getSource();
                                boolean selected = abstractButton.getModel().isSelected();
                                
                                
                                if(selected) {
                                    splitPane.setDividerLocation(0.75);
                                    // splitPane.setVisible(true);  
                                    listPane.setVisible(true);
                                }
                                else {
                                    splitPane.setDividerLocation(1.0);
                                    // splitPane.setVisible(false);  
                                    listPane.setVisible(false);
                                }

                                gui.repaint();
                            }
                        });              
                                                
                        
                        openSlideToolBar.add(viewingAnnotationButton);
                        openSlideToolBar.add(rectangularAnnotationButton);
                        openSlideToolBar.add(ellipseAnnotationButton);
                        openSlideToolBar.add(freehandAnnotationButton);
                        openSlideToolBar.addSeparator();
                        openSlideToolBar.add(selectionDeleteButton);
                        openSlideToolBar.add(selectionSaveButton);
                        openSlideToolBar.addSeparator();
                        openSlideToolBar.add(selectionPanelButton);
                        
                        
	
        
                        
                        jf.getContentPane().add(openSlideToolBar, BorderLayout.NORTH);
                        
                        
                        
        
        
                        final JLabel l = new JLabel(" ");
                        // System.out.println("properties:");
                        // System.out.println(os.getProperties());

                        jf.getContentPane().add(l, BorderLayout.SOUTH);
                        wv.addMouseMotionListener(new MouseMotionAdapter() {
                            @Override
                            public void mouseMoved(MouseEvent e) {
                                long x = wv.getSlideX(e.getX());
                                long y = wv.getSlideY(e.getY());
                                l.setText("(" + x + "," + y + ")");
                            }
                        });
                        wv.addMouseListener(new MouseAdapter() {
                            @Override
                            public void mouseExited(MouseEvent e) {
                                l.setText(" ");
                            }
                        });

                        /*
                        for (AssociatedImage img : os.getAssociatedImages()
                                .values()) {
                            JFrame j = new JFrame(img.getName());
                            j.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                            j.add(new JLabel(new ImageIcon(img.toBufferedImage())));
                            j.pack();
                            j.setVisible(true);
                        }
                        */
                        




                        
                        jf.setLocationByPlatform(true);
                        jf.setSize(900, 700);
                        jf.setVisible(true);

                                              
                        splitPane.setDividerLocation(1);
                        listPane.setVisible(false);
                        
                        /*
                        JFrame listFrame = new JFrame("selections");
                        listFrame.add(new JScrollPane(new JList(wv.getSelectionListModel()))); 
                        
                        JToolBar selectionToolBar = new JToolBar();
                        // mnemonics stop working in a floated toolbar
                        selectionToolBar.setFloatable(false);
                        
                        
                        selectionDeleteButton.setBorderPainted(false);
                        selectionSaveButton.setBorderPainted(false);
                        
                        selectionToolBar.add(selectionDeleteButton);
                        selectionToolBar.add(selectionSaveButton);
                        
                        selectionDeleteButton.addActionListener(new ActionListener() { 
                            public void actionPerformed(ActionEvent e)
                            {
                                // delete...
                            }
                        }); 
                        
                        selectionSaveButton.addActionListener(new ActionListener() { 
                            public void actionPerformed(ActionEvent e)
                            {
                                // delete...
                            }
                        }); 
                        
                        listFrame.add(selectionToolBar, BorderLayout.SOUTH);
                        
                        listFrame.pack();
                        listFrame.setVisible(true); 
                        */
                        
                    } catch(Throwable t) {
                        showThrowable(t);
                    }
                    gui.repaint();
                }
            });
            toolBar.add(openFile);

            showProperties = new JButton("Properties");
            showProperties.setMnemonic('p');
            showProperties.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent ae) {
                    try {
                        OpenSlide os;
                        os = new OpenSlide(currentFile);
                        
                        Map<String, String> properties = os.getProperties();
                        List<Object[]> propList = new ArrayList<Object[]>();
                        SortedSet<Map.Entry<String, String>> sorted = new TreeSet<Map.Entry<String, String>>(
                                new Comparator<Map.Entry<String, String>>() {
                                    @Override
                                    public int compare(Map.Entry<String, String> o1,
                                            Map.Entry<String, String> o2) {
                                        String k1 = o1.getKey();
                                        String k2 = o2.getKey();
                                        return k1.compareTo(k2);
                                    }
                                });
                        sorted.addAll(properties.entrySet());
                        for (Map.Entry<String, String> e : sorted) {
                            propList.add(new Object[] { e.getKey(), e.getValue() });
                        }
                        JTable propTable = new JTable(propList
                                .toArray(new Object[1][0]), new String[] { "key",
                                "value" }) {
                            @Override
                            public boolean isCellEditable(int row, int column) {
                                return false;
                            }
                        };
                        JFrame propFrame = new JFrame("properties");
                        propFrame.add(new JScrollPane(propTable));
                        propFrame.setLocationByPlatform(true);
                        propFrame.pack();
                        propFrame.setVisible(true);
                        
                    } catch(Throwable t) {
                        showThrowable(t);
                    }
                }
            });
            toolBar.add(showProperties);

            showThumbnail = new JButton("Thumbnail");
            showThumbnail.setMnemonic('p');
            showThumbnail.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent ae) {
                    try {
                        OpenSlide os;
                        os = new OpenSlide(currentFile);
                        
                        for (AssociatedImage img : os.getAssociatedImages().values()) {
                            JFrame j = new JFrame(img.getName());
                            j.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                            j.add(new JLabel(new ImageIcon(img.toBufferedImage())));
                            j.setLocationByPlatform(true);
                            j.pack();
                            j.setVisible(true);
                        }
                        
                        
                        
                        
                    } catch(Throwable t) {
                        showThrowable(t);
                    }
                }
            });
            toolBar.add(showThumbnail);
            
            
            selection = new JButton("Selection");
            selection.setMnemonic('s');

            selection.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent ae) {
                    try {
                                        
                    } catch(Throwable t) {
                        showThrowable(t);
                    }
                    gui.repaint();
                }
            });
            toolBar.add(selection);
            
            

                            
                            
                            
                            
                            
            locateFile = new JButton("Locate");
            locateFile.setMnemonic('l');

            locateFile.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent ae) {
                    try {
                        System.out.println("Locate: " + currentFile.getParentFile());
                        desktop.open(currentFile.getParentFile());
                    } catch(Throwable t) {
                        showThrowable(t);
                    }
                    gui.repaint();
                }
            });
            toolBar.add(locateFile);

            // Check the actions are supported on this platform!
            openFile.setEnabled(true);
            showProperties.setEnabled(true);
            showThumbnail.setEnabled(true);

            flags.add(new JLabel("::  Flags"));
            readable = new JCheckBox("Read  ");
            readable.setMnemonic('a');
            flags.add(readable);

            writable = new JCheckBox("Write  ");
            writable.setMnemonic('w');
            flags.add(writable);

            executable = new JCheckBox("Execute");
            executable.setMnemonic('x');
            flags.add(executable);

            int count = fileDetailsLabels.getComponentCount();
            for (int ii=0; ii<count; ii++) {
                fileDetailsLabels.getComponent(ii).setEnabled(false);
            }

            count = flags.getComponentCount();
            for (int ii=0; ii<count; ii++) {
                flags.getComponent(ii).setEnabled(false);
            }

            JPanel fileView = new JPanel(new BorderLayout(3,3));

            fileView.add(toolBar,BorderLayout.NORTH);
            fileView.add(fileMainDetails,BorderLayout.CENTER);

            detailView.add(fileView, BorderLayout.SOUTH);

            JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                treeScroll,
                detailView);
            gui.add(splitPane, BorderLayout.CENTER);

            JPanel simpleOutput = new JPanel(new BorderLayout(3,3));
            progressBar = new JProgressBar();
            simpleOutput.add(progressBar, BorderLayout.EAST);
            progressBar.setVisible(false);

            gui.add(simpleOutput, BorderLayout.SOUTH);

        }
        return gui;
    }

    public void showRootFile() {
        // ensure the main files are displayed
        tree.setSelectionInterval(0,0);
    }

    private TreePath findTreePath(File find) {
        for (int ii=0; ii<tree.getRowCount(); ii++) {
            TreePath treePath = tree.getPathForRow(ii);
            Object object = treePath.getLastPathComponent();
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)object;
            File nodeFile = (File)node.getUserObject();

            if (nodeFile==find) {
                return treePath;
            }
        }
        // not found!
        return null;
    }

    private void showErrorMessage(String errorMessage, String errorTitle) {
        JOptionPane.showMessageDialog(
            gui,
            errorMessage,
            errorTitle,
            JOptionPane.ERROR_MESSAGE
            );
    }

    private void showThrowable(Throwable t) {
        t.printStackTrace();
        JOptionPane.showMessageDialog(
            gui,
            t.toString(),
            t.getMessage(),
            JOptionPane.ERROR_MESSAGE
            );
        gui.repaint();
    }

    /** Update the table on the EDT */
    private void setTableData(final File[] files) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (fileTableModel==null) {
                    fileTableModel = new FileTableModel();
                    table.setModel(fileTableModel);
                }
                
                table.getSelectionModel().removeListSelectionListener(listSelectionListener);
                fileTableModel.setFiles(files);
                table.getSelectionModel().addListSelectionListener(listSelectionListener);
                if (!cellSizesSet) {
                    Icon icon = fileSystemView.getSystemIcon(files[0]);

                    // size adjustment to better account for icons
                    table.setRowHeight( icon.getIconHeight()+rowIconPadding );

                    setColumnWidth(0,-1);
                    /*
                    setColumnWidth(3,60);
                    table.getColumnModel().getColumn(3).setMaxWidth(120);
                    setColumnWidth(4,-1);
                    setColumnWidth(5,-1);
                    setColumnWidth(6,-1);
                    setColumnWidth(7,-1);
                    setColumnWidth(8,-1);
                    setColumnWidth(9,-1);
                    */
                    cellSizesSet = true;
                }
            }
        });
    }

    private void setColumnWidth(int column, int width) {
        TableColumn tableColumn = table.getColumnModel().getColumn(column);
        if (width<0) {
            // use the preferred width of the header..
            JLabel label = new JLabel( (String)tableColumn.getHeaderValue() );
            Dimension preferred = label.getPreferredSize();
            // altered 10->14 as per camickr comment.
            width = (int)preferred.getWidth()+14;
        }
        tableColumn.setPreferredWidth(width);
        tableColumn.setMaxWidth(width);
        tableColumn.setMinWidth(width);
    }

    /** Add the files that are contained within the directory of this node.
    Thanks to Hovercraft Full Of Eels for the SwingWorker fix. */
    private void showChildren(final DefaultMutableTreeNode node) {
        tree.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);

        SwingWorker<Void, File> worker = new SwingWorker<Void, File>() {
            @Override
            public Void doInBackground() {
                

                    
                File file = (File) node.getUserObject();
                if (file.isDirectory()) {
                    
                    File[] files = fileSystemView.getFiles(file, true); //!!
                    Arrays.sort(files);
                    if (node.isLeaf()) {
                        for (File child : files) {
                            if (child.isDirectory()) {
                                publish(child);
                            }
                        }
                    }
                    
                    
                    FileFilter fileFilter = new FileFilter() {
                        public boolean accept(File file) {
                                return !file.getName().endsWith(".osa") && !file.getName().startsWith(".") && file.isFile();
                        }
                    };
                    
                    File[] onlyFiles = file.listFiles(fileFilter);
                    Arrays.sort(onlyFiles);
                    
                    
                    setTableData(onlyFiles);
                }
                return null;
            }

            @Override
            protected void process(List<File> chunks) {
                for (File child : chunks) {
                    node.add(new DefaultMutableTreeNode(child));
                }
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                progressBar.setVisible(false);
                tree.setEnabled(true);
            }
        };
        worker.execute();
    }

    /** Update the File details view with the details of this File. */
    private void setFileDetails(File file) {
        currentFile = file;
        Icon icon = fileSystemView.getSystemIcon(file);
        fileName.setIcon(icon);
        fileName.setText(fileSystemView.getSystemDisplayName(file));
        path.setText(file.getPath());
        date.setText(new Date(file.lastModified()).toString());
        size.setText(file.length() + " bytes");
        readable.setSelected(file.canRead());
        writable.setSelected(file.canWrite());
        executable.setSelected(file.canExecute());
        isDirectory.setSelected(file.isDirectory());

        isFile.setSelected(file.isFile());

        JFrame f = (JFrame)gui.getTopLevelAncestor();
        if (f!=null) {
            f.setTitle(
                APP_TITLE +
                " :: " +
                fileSystemView.getSystemDisplayName(file) );
        }

        gui.repaint();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    // Significantly improves the look of the output in
                    // terms of the file names returned by FileSystemView!
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch(Exception weTried) {
                }
                JFrame f = new JFrame(APP_TITLE);
                f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                

                FileBrowser FileBrowser = new FileBrowser();
                f.setContentPane(FileBrowser.getGui());

                try {
                    URL urlBig = FileBrowser.getClass().getResource("fb-icon-32x32.png");
                    URL urlSmall = FileBrowser.getClass().getResource("fb-icon-16x16.png");
                    ArrayList<Image> images = new ArrayList<Image>();
                    images.add( ImageIO.read(urlBig) );
                    images.add( ImageIO.read(urlSmall) );
                    f.setIconImages(images);
                } catch(Exception weTried) {}

                f.pack();
                f.setLocationByPlatform(true);
                f.setMinimumSize(f.getSize());
                f.setVisible(true);

                FileBrowser.showRootFile();
            }
        });
    }
}

/** A TableModel to hold File[]. */
class FileTableModel extends AbstractTableModel {

    private File[] files;
    private FileSystemView fileSystemView = FileSystemView.getFileSystemView();
    private String[] columns = {
        "Icon",
        "File",
        // "Path/name",
        // "Size",
        // "Last Modified",
        // "R",
        // "W",
        // "E",
        // "D",
        // "F",
    };

    FileTableModel() {
        this(new File[0]);
    }

    FileTableModel(File[] files) {
        this.files = files;
    }

    public Object getValueAt(int row, int column) {
        File file = files[row];
        switch (column) {
            case 0:
                return fileSystemView.getSystemIcon(file);
            case 1:
                return fileSystemView.getSystemDisplayName(file);
            /*
            case 2:
                return file.getPath();
            case 3:
                return file.length();
            case 4:
                return file.lastModified();
            case 5:
                return file.canRead();
            case 6:
                return file.canWrite();
            case 7:
                return file.canExecute();
            case 8:
                return file.isDirectory();
            case 9:
                return file.isFile();
            */
            default:
                System.err.println("Logic Error");
        }
        return "";
    }

    public int getColumnCount() {
        return columns.length;
    }

    public Class<?> getColumnClass(int column) {
        switch (column) {
            case 0:
                return ImageIcon.class;
            /*
            case 3:
                return Long.class;
            case 4:
                return Date.class;
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
                return Boolean.class;
            */
        }
        return String.class;
    }

    public String getColumnName(int column) {
        return columns[column];
    }

    public int getRowCount() {
        return files.length;
    }

    public File getFile(int row) {
        return files[row];
    }

    public void setFiles(File[] files) {
        this.files = files;
        fireTableDataChanged();
    }
}

/** A TreeCellRenderer for a File. */
class FileTreeCellRenderer extends DefaultTreeCellRenderer {

    private FileSystemView fileSystemView;

    private JLabel label;

    FileTreeCellRenderer() {
        label = new JLabel();
        label.setOpaque(true);
        fileSystemView = FileSystemView.getFileSystemView();
    }

    @Override
    public Component getTreeCellRendererComponent(
        JTree tree,
        Object value,
        boolean selected,
        boolean expanded,
        boolean leaf,
        int row,
        boolean hasFocus) {

        DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
        File file = (File)node.getUserObject();
        label.setIcon(fileSystemView.getSystemIcon(file));
        label.setText(fileSystemView.getSystemDisplayName(file));
        label.setToolTipText(file.getPath());

        if (selected) {
            label.setBackground(backgroundSelectionColor);
            label.setForeground(textSelectionColor);
        } else {
            label.setBackground(backgroundNonSelectionColor);
            label.setForeground(textNonSelectionColor);
        }

        return label;
    }
}