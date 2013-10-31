package com.seigneurin.adManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Assert;
import org.yaml.snakeyaml.Yaml;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlFileInput;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;
import com.gargoylesoftware.htmlunit.html.HtmlTextArea;

public class Main {

    private static Logger logger = Logger.getLogger("com.seigneurin.adManager");

    private static String pathname;

    private static SellerSettings sellerSettings;
    private static ObjectSettings objectSettings;

    public static void main(String[] args) throws Exception {
        Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);

        parseArguments(args);
        loadConfiguration();
        postAd();
    }

    private static void parseArguments(String[] args) {
        if (args.length != 1)
            printUsageAndExit("Missing path.");

        pathname = args[0];
    }

    private static void printUsageAndExit(String reason) {
        if (reason != null) {
            System.out.println(reason);
            System.out.println();
        }

        System.out.println("Usage: PATH");
        System.out.println("  PATH: path to directory containing a settings.yaml file and a JPG photo.");
        System.exit(1);
    }

    private static void postAd() throws Exception {
        WebClient webClient = new WebClient(BrowserVersion.FIREFOX_3);
        webClient.setThrowExceptionOnScriptError(false);

        // load the home page

        logger.log(Level.INFO, "Chargement de la page d'accueil...");
        HtmlPage homePage = webClient.getPage("http://www.leboncoin.fr/");
        Assert.assertEquals("Petites annonces gratuites d'occasion - leboncoin.fr", homePage.getTitleText());

        // click the link to the region

        logger.log(Level.INFO, "Sélection de la région...");
        HtmlAnchor regionLink = homePage.getFirstAnchorByText(sellerSettings.region);
        Assert.assertNotNull(regionLink);

        HtmlPage regionHomePage = regionLink.click();
        Assert.assertNotNull(regionHomePage);
        Assert.assertEquals("Annonces " + sellerSettings.region + " - leboncoin.fr", regionHomePage.getTitleText());

        // click the link to the page to post an ad

        logger.log(Level.INFO, "Chargement de la page de dépôt d'annonces...");
        HtmlAnchor postAdLink = regionHomePage.getFirstAnchorByText("Déposer une annonce");
        Assert.assertNotNull(postAdLink);

        HtmlPage postAdPage = postAdLink.click();
        Assert.assertNotNull(postAdPage);
        Assert.assertEquals("Formulaire de dépôt de petites annonces gratuites sur Leboncoin.fr",
                postAdPage.getTitleText());

        // fill-in the form

        HtmlForm form = postAdPage.getFormByName("formular");

        logger.log(Level.INFO, "Remplissage des champs 'Localisation'...");

        HtmlSelect regionElement = form.getSelectByName("region");
        selectOption(regionElement, sellerSettings.region);

        HtmlSelect departementElement = form.getSelectByName("dpt_code");
        selectOption(departementElement, sellerSettings.departement);

        HtmlInput zipCodeElement = (HtmlInput) form.getInputByName("zipcode");
        zipCodeElement.setValueAttribute(sellerSettings.zipCode);

        logger.log(Level.INFO, "Remplissage des champs 'Catégorie'...");

        HtmlSelect categoryElement = form.getSelectByName("category");
        selectOption(categoryElement, objectSettings.category);

        logger.log(Level.INFO, "Remplissage des champs 'Vos informations'...");

        HtmlInput nameElement = form.getInputByName("name");
        nameElement.setValueAttribute(sellerSettings.name);

        HtmlInput emailElement = form.getInputByName("email");
        emailElement.setValueAttribute(sellerSettings.email);

        HtmlInput phoneElement = form.getInputByName("phone");
        phoneElement.setValueAttribute(sellerSettings.phoneNumber);

        HtmlInput phoneHiddenElement = form.getInputByName("phone_hidden");
        phoneHiddenElement.setChecked(true);

        logger.log(Level.INFO, "Remplissage des champs 'Votre annonce'...");

        HtmlInput subjectElement = form.getInputByName("subject");
        subjectElement.setValueAttribute(objectSettings.subject);

        HtmlTextArea bodyElement = (HtmlTextArea) postAdPage.getElementById("body");
        bodyElement.setText(objectSettings.body);

        HtmlInput priceElement = form.getInputByName("price");
        priceElement.setValueAttribute(objectSettings.price);

        HtmlFileInput imageInput = (HtmlFileInput) postAdPage.getElementById("image0");
        imageInput.setValueAttribute(objectSettings.imagePath);
        imageInput.setContentType("image/jpeg");
        // HtmlSubmitInput uploadButton = (HtmlSubmitInput)
        // postAdPage.getFirstByXPath("//input[@class='button-upload']");
        // postAdPage = uploadButton.click();

        logger.log(Level.INFO, "Validation...");

        HtmlInput validateButton = form.getInputByName("validate");
        HtmlPage validationPage = validateButton.click();
        Assert.assertNotNull(validationPage);

        List<?> errorsElements = validationPage.getByXPath("//span[@class='error']");
        for (Object error : errorsElements) {
            HtmlElement errorElement = (HtmlElement) error;
            if ("".equals(errorElement.asText()) == false)
                System.err.println("Error: " + errorElement.asText());
        }

        Assert.assertEquals("Vérifiez votre annonce.", validationPage.getTitleText());

        HtmlElement photoElement = validationPage.getFirstByXPath("//div[text()='Photo principale']");
        Assert.assertNotNull("Photo was not uploaded", photoElement);

        form = validationPage.getFormByName("formular");

        logger.log(Level.INFO, "Remplissage des champs 'Vérifiez le contenu de votre annonce'...");

        HtmlSelect cityElement = form.getSelectByName("city");
        selectOption(cityElement, sellerSettings.city);

        HtmlInput passwordElement = form.getInputByName("passwd");
        passwordElement.setValueAttribute(sellerSettings.password);

        HtmlInput passwordVerElement = form.getInputByName("passwd_ver");
        passwordVerElement.setValueAttribute(sellerSettings.password);

        HtmlInput createButton = form.getInputByName("create");
        HtmlPage creationPage = createButton.click();

        logger.log(Level.INFO, "Terminé !");

        HtmlElement mainTextElement = creationPage.getFirstByXPath("//div[@class='maintext']");
        System.out.println(mainTextElement.asText());
    }

    private static void loadConfiguration() throws FileNotFoundException {
        Yaml yaml = new Yaml();

        File path = new File(pathname);

        String sellerYamlFilename = path.getParent() + File.separator + "settings.yaml";
        logger.log(Level.INFO, "Chargement des propriétés du vendeur : " + sellerYamlFilename);
        FileInputStream mainYamlFileStream = new FileInputStream(sellerYamlFilename);
        sellerSettings = yaml.loadAs(mainYamlFileStream, SellerSettings.class);

        String objectYamlFilename = path + File.separator + "settings.yaml";
        logger.log(Level.INFO, "Chargement des propriétés de l'objet : " + objectYamlFilename);
        FileInputStream objectYamlFileStream = new FileInputStream(objectYamlFilename);
        objectSettings = yaml.loadAs(objectYamlFileStream, ObjectSettings.class);

        String[] imageFiles = path.list(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".jpg");
            }
        });
        if (imageFiles.length >= 1)
            objectSettings.imagePath = path + File.separator + imageFiles[0];
    }

    private static void selectOption(HtmlSelect selectElement, String optionText) {
        Assert.assertNotNull(selectElement);
        HtmlOption regionOption = findOption(selectElement, optionText);
        selectElement.setSelectedAttribute(regionOption, true);
        Assert.assertEquals(1, selectElement.getSelectedOptions().size());
        Assert.assertEquals(optionText, selectElement.getSelectedOptions().get(0).asText());
    }

    private static HtmlOption findOption(HtmlSelect selectElement, String optionText) {
        for (HtmlOption option : selectElement.getOptions())
            if (optionText.equals(option.getText()))
                return option;
        Assert.assertTrue("No option with text: " + optionText, false);
        return null;
    }

}
