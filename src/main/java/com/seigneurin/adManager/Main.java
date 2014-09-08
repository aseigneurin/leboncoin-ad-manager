package com.seigneurin.adManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URLConnection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Assert;
import org.yaml.snakeyaml.Yaml;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;
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

    private static String action;
    private static String pathname;

    private static SellerSettings sellerSettings;
    private static ObjectSettings objectSettings;
    private static WebClient webClient;

    public static void main(String[] args) throws Exception {
        Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);

        parseArguments(args);
        loadConfiguration();
        if ("unpublish".equals(action) || "republish".equals(action))
            deleteAd();
        if ("publish".equals(action) || "republish".equals(action))
            postAd();
    }

    private static void parseArguments(String[] args) {
        if (args.length < 2)
            printUsageAndExit("Missing parameter.");
        if (args.length > 2)
            printUsageAndExit("Too many parameters.");

        action = args[0];
        if ("publish".equals(action) == false && "unpublish".equals(action) == false
                && "republish".equals(action) == false)
            printUsageAndExit("Invalid ACTION.");

        pathname = args[1];
    }

    private static void printUsageAndExit(String reason) {
        if (reason != null) {
            System.out.println(reason);
            System.out.println();
        }

        System.out.println("Usage: ACTION PATH");
        System.out.println("  ACTION: publish, republish");
        System.out.println("  PATH: path to directory containing a settings.yaml file and a JPG photo.");
        System.exit(1);
    }

    private static void prepareWebClient() {
        webClient = new WebClient(BrowserVersion.FIREFOX_3);
        webClient.setThrowExceptionOnScriptError(false);
    }

    private static HtmlPage loadAccountPage() throws IOException {
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

        // fill-in the login form

        HtmlForm form = regionHomePage.getFormByName("loginform");

        logger.log(Level.INFO, "Connexion au compte utilisateur...");

        HtmlInput usernameElement = (HtmlInput) form.getInputByName("st_username");
        usernameElement.setValueAttribute(sellerSettings.email);

        HtmlInput passwordElement = (HtmlInput) form.getInputByName("st_passwd");
        passwordElement.setValueAttribute(sellerSettings.password);

        HtmlInput okButton = form.getFirstByXPath("//input[@type='submit']");
        HtmlPage accountPage = okButton.click();
        Assert.assertNotNull(accountPage);
        Assert.assertEquals("Compte", accountPage.getTitleText());
        return accountPage;
    }

    private static void postAd() throws Exception {
        prepareWebClient();
        HtmlPage accountPage = loadAccountPage();

        // click the link to the page to post an ad

        logger.log(Level.INFO, "Chargement de la page de dépôt d'annonces...");
        HtmlAnchor postAdLink = accountPage.getFirstAnchorByText("Déposer une annonce");
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

        int nbImages = java.lang.Math.min(3, objectSettings.imageFiles.length);
        for (int i = 0; i < nbImages; i++) {
            HtmlFileInput imageInput = (HtmlFileInput) postAdPage.getElementById("image" + i);
            String imagePath = objectSettings.imageFiles[i].getAbsolutePath();
            imageInput.setValueAttribute(imagePath);
            String contentType = URLConnection.guessContentTypeFromName(imagePath);
            imageInput.setContentType(contentType);
            // HtmlSubmitInput uploadButton = (HtmlSubmitInput)
            // postAdPage.getFirstByXPath("//input[@class='button-upload']");
            // postAdPage = uploadButton.click();
        }

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

        try {
            HtmlSelect cityElement = form.getSelectByName("city");
            selectOption(cityElement, sellerSettings.city);
        } catch (ElementNotFoundException e) {
            logger.log(Level.INFO, "Pas de champs 'city'");
        }

        HtmlCheckBoxInput acceptRuleCheckBox = form.getInputByName("accept_rule");
        acceptRuleCheckBox.setChecked(true);

        HtmlInput createButton = form.getInputByName("create");
        HtmlPage creationPage = createButton.click();

        logger.log(Level.INFO, "Terminé !");

        Assert.assertEquals("Confirmation d'envoi de votre annonce", creationPage.getTitleText());
        Assert.assertTrue(creationPage.asText().contains("Votre annonce a été envoyée à notre équipe éditoriale."));
    }

    private static void deleteAd() throws Exception {
        prepareWebClient();
        HtmlPage accountPage = loadAccountPage();

        logger.log(Level.INFO, "Chargement de la page de l'objet...");

        HtmlAnchor objectLink = accountPage.getFirstAnchorByText(objectSettings.subject);
        HtmlPage objectPage = objectLink.click();
        Assert.assertNotNull(objectPage);
        Assert.assertTrue("Unexpected page: " + objectPage.getTitleText(),
                objectPage.getTitleText().startsWith(objectSettings.subject));

        logger.log(Level.INFO, "Chargement de la page de suppression de l'objet...");

        HtmlAnchor deleteLink = objectPage.getFirstAnchorByText("Supprimer");
        HtmlPage deleteObjectPage = webClient.getPage(deleteLink.getHrefAttribute());
        //HtmlPage deleteObjectPage = deleteLink.click();
        Assert.assertNotNull(deleteObjectPage);
        Assert.assertEquals("Leboncoin.fr - Gestion de l'annonce", deleteObjectPage.getTitleText());

        logger.log(Level.INFO, "Suppression de l'objet...");

        HtmlInput continueButton = deleteObjectPage.getFirstByXPath("//input[@id='store__continue']");
        HtmlPage deletionPage = continueButton.click();
        Assert.assertEquals("Confirmation de votre suppression", deletionPage.getTitleText());

        logger.log(Level.INFO, "Validation...");

        HtmlInput validateButton = (HtmlInput) deletionPage.getElementById("st_ads_continue");
        HtmlPage endPage = validateButton.click();
        Assert.assertTrue(endPage.asText().contains("Votre demande de suppression"));
        Assert.assertTrue(endPage.asText().contains("bien été prise en compte."));
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

        objectSettings.imageFiles = path.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".png");
            }
        });
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
