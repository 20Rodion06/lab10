package etu.java.lab10;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Program for cinema
 *
 * @author Rodion
 * @version 1.0
 * @since 2024
 */

public class Application {
    private static final Logger logger = LogManager.getLogger(Application.class);
    private JFrame window;
    private JToolBar ButtonsPanel;
    private JButton save, open, add, edit, delete, info, filter;
    private DefaultTableModel model;
    private JTable films;
    private JComboBox<String> Name;
    private JTextField filmName;
    private JPanel filterPanel;
    private JButton generateHTML;
    private JButton generatePDFfromHTML;
    private ExecutorService executor = Executors.newFixedThreadPool(3);
    private CountDownLatch loadComplete = new CountDownLatch(1);
    private CountDownLatch editComplete = new CountDownLatch(1);

    public void show() {
        logger.info("Initializing application UI");
        logger.debug("Setting up UI components");
        window = new JFrame("Список фильмов");
        ButtonsPanel = new JToolBar();
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setSize(800, 400);
        window.setLocationRelativeTo(null);

        save = new JButton(new ImageIcon("./src/Icons/save-20x20.png"));
        open = new JButton(new ImageIcon("./src/Icons/open-20x20.png"));
        add = new JButton(new ImageIcon("./src/Icons/add-20x20.png"));
        edit = new JButton(new ImageIcon("./src/Icons/edit-20x20.jpg"));
        delete = new JButton(new ImageIcon("./src/Icons/trash-20x20.png"));
        info = new JButton(new ImageIcon("./src/Icons/info-20x20.png"));
        generateHTML = new JButton(new ImageIcon("./src/Icons/html-20x20.png"));
        generatePDFfromHTML = new JButton(new ImageIcon("./src/Icons/pdf-20x20.png"));

        ButtonsPanel.add(save);
        ButtonsPanel.add(open);
        ButtonsPanel.add(add);
        ButtonsPanel.add(edit);
        ButtonsPanel.add(delete);
        ButtonsPanel.add(info);
        ButtonsPanel.add(generateHTML);
        ButtonsPanel.add(generatePDFfromHTML);
        window.getContentPane().add(BorderLayout.NORTH, ButtonsPanel);

        String[] columns = {"Фильм", "Жанр", "Сеанс", "Проданные билеты"};
        Object[][] data = {
                {"Форсаж 2", "Боевик", "19:30", "147"},
                {"Стражи галактики 2", "Научная фантастика", "14:30", "182"},
                {"Матрица", "Научная фантастика", "17:00", "156"},
        };
        model = new DefaultTableModel(data, columns);
        films = new JTable(model);

        window.add(BorderLayout.CENTER, new JScrollPane(films));

        Name = new JComboBox<>(new String[]{"Фильм", "Жанр", "Сеанс"});
        filmName = new JTextField("Название фильма");
        filter = new JButton("Поиск");
        filterPanel = new JPanel();
        filterPanel.add(Name);
        filterPanel.add(filmName);
        filterPanel.add(filter);
        window.add(BorderLayout.SOUTH, filterPanel);

        logger.debug("Adding action listeners");
        add.addActionListener(new AddButtonListener());
        delete.addActionListener(new DeleteButtonListener());
        filter.addActionListener(new FilterButtonListener());
        open.addActionListener(e -> {
            logger.info("Open button clicked");
            executor.submit(this::loadDataTask);
        });
        save.addActionListener(e -> {
            logger.info("Save button clicked");
            executor.submit(this::editDataTask);
        });
        generateHTML.addActionListener(e -> {
            logger.info("Generate HTML button clicked");
            executor.submit(this::generateHTMLTask);
        });
        generatePDFfromHTML.addActionListener(new GeneratePDFReportButtonListener());

        logger.info("Application UI initialized successfully");
        window.setVisible(true);
    }

    public void saveDataToXml(File file) {
        logger.info("Saving data to XML file: {}", file.getPath());
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            Element rootElement = doc.createElement("films");
            doc.appendChild(rootElement);

            for (int i = 0; i < model.getRowCount(); i++) {
                Element film = doc.createElement("film");

                Element name = doc.createElement("name");
                name.appendChild(doc.createTextNode((String) model.getValueAt(i, 0)));
                film.appendChild(name);

                Element genre = doc.createElement("genre");
                genre.appendChild(doc.createTextNode((String) model.getValueAt(i, 1)));
                film.appendChild(genre);

                Element time = doc.createElement("time");
                time.appendChild(doc.createTextNode((String) model.getValueAt(i, 2)));
                film.appendChild(time);

                Element ticketsSold = doc.createElement("ticketsSold");
                ticketsSold.appendChild(doc.createTextNode((String) model.getValueAt(i, 3)));
                film.appendChild(ticketsSold);

                rootElement.appendChild(film);
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(file);
            transformer.transform(source, result);

            logger.info("Data saved to XML successfully");
            JOptionPane.showMessageDialog(window, "Данные успешно сохранены в XML!");
        } catch (Exception e) {
            logger.error("Error saving data to XML: {}", e.getMessage(), e);
            JOptionPane.showMessageDialog(window, "Ошибка сохранения данных в XML: " + e.getMessage());
        }
    }

    public void loadDataFromXml(File file) {
        logger.info("Loading data from XML file: {}", file.getPath());
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);
            doc.getDocumentElement().normalize();

            model.setRowCount(0);

            NodeList filmList = doc.getElementsByTagName("film");

            for (int i = 0; i < filmList.getLength(); i++) {
                Node filmNode = filmList.item(i);

                if (filmNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element filmElement = (Element) filmNode;

                    String name = filmElement.getElementsByTagName("name").item(0).getTextContent();
                    String genre = filmElement.getElementsByTagName("genre").item(0).getTextContent();
                    String time = filmElement.getElementsByTagName("time").item(0).getTextContent();
                    String ticketsSold = filmElement.getElementsByTagName("ticketsSold").item(0).getTextContent();

                    model.addRow(new Object[]{name, genre, time, ticketsSold});
                }
            }
            logger.info("Data loaded successfully from XML");
            JOptionPane.showMessageDialog(window, "Данные успешно загружены из XML!");
        } catch (Exception e) {
            logger.error("Error loading data from XML: {}", e.getMessage(), e);
            JOptionPane.showMessageDialog(window, "Ошибка загрузки данных из XML: " + e.getMessage());
        }
    }

    public class AddButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            logger.debug("Add button clicked");
            try {
                String name = filmName.getText().trim();
                if (name.isEmpty()) {
                    logger.warn("Attempt to add film with empty name");
                    throw new EmptyFilmNameExcept("Название фильма не должно быть пустым.");
                }
                model.addRow(new Object[]{name, "Жанр", "Сеанс", "0"});
                logger.info("Film \"{}\" added successfully", name);
                JOptionPane.showMessageDialog(window, "Фильм \"" + name + "\" добавлен!");
            } catch (EmptyFilmNameExcept ex) {
                logger.error("Failed to add film: {}", ex.getMessage(), ex);
                JOptionPane.showMessageDialog(window, ex.getMessage());
            }
        }
    }

    public class DeleteButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                int selectedRow = films.getSelectedRow();
                if (selectedRow == -1) {
                    throw new NoSelectExcept("Выберите фильм, который надо удалить.");
                }
                model.removeRow(selectedRow);
                logger.info("Film deleted successfully");
                JOptionPane.showMessageDialog(window, "Фильм удален!");
            } catch (NoSelectExcept ex) {
                logger.error("Failed to delete film: {}", ex.getMessage(), ex);
                JOptionPane.showMessageDialog(window, ex.getMessage());
            }
        }
    }

    public class FilterButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String searchCriteria = filmName.getText().toLowerCase();
            String selectedOption = (String) Name.getSelectedItem();
            StringBuilder result = new StringBuilder("Результаты поиска:\n");
            boolean found = false;

            for (int i = 0; i < model.getRowCount(); i++) {
                String film = (String) model.getValueAt(i, 0);
                String genre = (String) model.getValueAt(i, 1);
                String time = (String) model.getValueAt(i, 2);

                if ((selectedOption.equals("Фильм") && film.toLowerCase().contains(searchCriteria)) ||
                        (selectedOption.equals("Жанр") && genre.toLowerCase().contains(searchCriteria)) ||
                        (selectedOption.equals("Сеанс") && time.toLowerCase().contains(searchCriteria))) {
                    result.append(film).append(", ").append(genre).append(", ").append(time).append("\n");
                    found = true;
                }
            }

            if (found) {
                logger.info("Films found matching search criteria");
                JOptionPane.showMessageDialog(window, result.toString());
            } else {
                logger.info("No films found matching search criteria");
                JOptionPane.showMessageDialog(window, "Фильм не найден.");
            }
        }
    }

    public class GenerateHTMLReportButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser fileChooser = new JFileChooser();
            if (fileChooser.showSaveDialog(window) == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                generateReport(file);
            }
        }

        private void generateReport(File file) {
            try {
                logger.info("Generating HTML report");
                StringBuilder htmlContent = new StringBuilder("""
                    <html>
                    <head>
                        <title>Film Report</title>
                        <style>
                            body { 
                                font-family: Arial, sans-serif;
                                margin: 0;
                                padding: 0;
                            }
                            .header {
                                background-color: #006699;
                                color: white;
                                padding: 20px;
                                overflow: hidden;
                            }
                            .header h1 {
                                float: left;
                                margin: 0;
                                font-size: 40px;
                            }
                            .subtitle {
                                float: right;
                                font-size: 14px;
                                opacity: 0.9;
                                margin: 0;
                                line-height: 40px;
                            }
                            .content {
                                padding: 20px;
                            }
                            table {
                                width: 100%;
                                border-collapse: collapse;
                                margin-top: 20px;
                            }
                            th, td {
                                border: 1px solid #ddd;
                                padding: 12px;
                                text-align: left;
                            }
                            th {
                                background-color: #f5f5f5;
                                font-weight: normal;
                            }
                            tr:nth-child(even) {
                                background-color: #f9f9f9;
                            }
                        </style>
                    </head>
                    <body>
                        <div class="header">
                            <h1>Отчет</h1>
                            <span class="subtitle">По лабораторной работе</span>
                        </div>
                        <div class="content">
                            <table>
                                <tr>
                                    <th>Фильм</th>
                                    <th>Жанр</th>
                                    <th>Сеанс</th>
                                    <th>Проданные</th>
                                </tr>
                """);
                for (int i = 0; i < model.getRowCount(); i++) {
                    String film = (String) model.getValueAt(i, 0);
                    String genre = (String) model.getValueAt(i, 1);
                    String time = (String) model.getValueAt(i, 2);
                    String ticketsSold = (String) model.getValueAt(i, 3);

                    htmlContent.append("<tr>")
                            .append("<td>").append(film).append("</td>")
                            .append("<td>").append(genre).append("</td>")
                            .append("<td>").append(time).append("</td>")
                            .append("<td>").append(ticketsSold).append("</td>")
                            .append("</tr>");
                }

                htmlContent.append("</table></div></body></html>");
                Files.write(file.toPath(), htmlContent.toString().getBytes());

                logger.info("HTML report generated successfully");
                JOptionPane.showMessageDialog(window, "Отчет успешно создан!");
            } catch (IOException ex) {
                logger.error("Error generating HTML report: {}", ex.getMessage(), ex);
                JOptionPane.showMessageDialog(window, "Ошибка создания отчета: " + ex.getMessage());
            }
        }
    }

    public class GeneratePDFReportButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser htmlChooser = new JFileChooser();
            htmlChooser.setDialogTitle("Выберите HTML-файл для конвертации");

            if (htmlChooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) {
                File htmlFile = htmlChooser.getSelectedFile();

                JFileChooser pdfChooser = new JFileChooser();
                pdfChooser.setDialogTitle("Сохранить PDF как");
                if (pdfChooser.showSaveDialog(window) == JFileChooser.APPROVE_OPTION) {
                    File pdfFile = pdfChooser.getSelectedFile();
                    if (!pdfFile.getName().toLowerCase().endsWith(".pdf")) {
                        pdfFile = new File(pdfFile.getAbsolutePath() + ".pdf");
                    }
                    convertHTMLtoPDF(htmlFile, pdfFile);
                }
            }
        }

        private void convertHTMLtoPDF(File htmlFile, File pdfFile) {
            try {
                logger.info("Converting HTML to PDF");
                PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile));
                PdfDocument pdf = new PdfDocument(writer);

                HtmlConverter.convertToPdf(new FileInputStream(htmlFile), pdf);

                logger.info("PDF generated successfully");
                JOptionPane.showMessageDialog(window, "PDF успешно создан!");
            } catch (Exception ex) {
                logger.error("Error generating PDF: {}", ex.getMessage(), ex);
                JOptionPane.showMessageDialog(window, "Ошибка создания PDF-файла: " + ex.getMessage());
            }
        }
    }

    public void loadDataTask() {
        try {
            JFileChooser fileChooser = new JFileChooser();
            if (fileChooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                loadDataFromXml(file);
                logger.info("Data loaded successfully");
            }
        } catch (Exception e) {
            logger.error("Error loading data: {}", e.getMessage(), e);
            JOptionPane.showMessageDialog(window, "Ошибка загрузки данных: " + e.getMessage());
        } finally {
            loadComplete.countDown();
        }
    }

    public void editDataTask() {
        try {
            loadComplete.await();
            JFileChooser fileChooser = new JFileChooser();
            if (fileChooser.showSaveDialog(window) == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                saveDataToXml(file);
                logger.info("Data saved successfully");
            }
        } catch (InterruptedException e) {
            logger.error("Edit operation interrupted: {}", e.getMessage(), e);
            JOptionPane.showMessageDialog(window, "Операция прервана: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error editing data: {}", e.getMessage(), e);
            JOptionPane.showMessageDialog(window, "Ошибка редактирования данных: " + e.getMessage());
        } finally {
            editComplete.countDown();
        }
    }

    public void generateHTMLTask() {
        try {
            JFileChooser fileChooser = new JFileChooser();
            if (fileChooser.showSaveDialog(window) == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                new GenerateHTMLReportButtonListener().generateReport(file);
                logger.info("HTML report generated successfully");
            }
        } catch (Exception e) {
            logger.error("Error generating HTML report: {}", e.getMessage(), e);
            JOptionPane.showMessageDialog(window, "Ошибка создания отчета: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        logger.info("Starting application");
        System.out.println("Starting application");
        System.out.println("Log4j configuration file: " + Application.class.getResource("/log4j2.xml"));
        new Application().show();
    }
}