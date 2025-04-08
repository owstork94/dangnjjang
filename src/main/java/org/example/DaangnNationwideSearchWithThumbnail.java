package org.example;

import javafx.application.Application;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DaangnNationwideSearchWithThumbnail extends Application {

    private TextField searchField;
    private Button searchButton;
    private TableView<ItemData> tableView;
    private List<String> resultUrls = new ArrayList<>();
    private Hyperlink saleUrlLink;
    private Hyperlink allUrlLink;
    private ComboBox<String> regionComboBox;

    private final String[][] regions = {
            {"가락동", "가락동-0001"}, {"가산동", "가산동-0002"}, {"가양동", "가양동-0003"},
            {"노형동", "노형동-3835"}, {"연동", "연동-3834"}, {"이도2동", "이도2동-3832"}
    };

    private final String[] provinces = {
            "서울특별시", "경기도", "강원도", "충청북도", "충청남도",
            "전라북도", "전라남도", "경상북도", "경상남도", "제주특별자치도"
    };

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("당근마켓 검색기 (지역 선택 포함)");

        searchField = new TextField();
        searchField.setPromptText("검색어 입력 (예: 드론)");

        List<String> regionList = new ArrayList<>();
        for (String[] region : regions) {
            regionList.add(region[0]);
        }
        regionList.addAll(Arrays.asList(provinces));

        ObservableList<String> observableRegions = FXCollections.observableArrayList(regionList);
        FilteredList<String> filteredRegions = new FilteredList<>(observableRegions, p -> true);

        regionComboBox = new ComboBox<>(filteredRegions);
        regionComboBox.setEditable(true);
        regionComboBox.setPromptText("지역 선택");

        regionComboBox.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
            if (regionComboBox.isShowing()) {
                return;
            }
            if (newValue == null || newValue.isEmpty()) {
                filteredRegions.setPredicate(p -> true);
            } else {
                final String lower = newValue.toLowerCase();
                filteredRegions.setPredicate(item -> item.toLowerCase().contains(lower));
            }
        });

        searchButton = new Button("검색");
        searchButton.setStyle("-fx-background-color: #FF6F0F; -fx-text-fill: white;");
        searchButton.setOnAction(e -> searchItems());

        saleUrlLink = new Hyperlink("거래 가능 매물 URL");
        saleUrlLink.setOnAction(e -> openWebpage(saleUrlLink.getText()));

        allUrlLink = new Hyperlink("전체 매물 URL");
        allUrlLink.setOnAction(e -> openWebpage(allUrlLink.getText()));

        VBox urlBox = new VBox(5, saleUrlLink, allUrlLink);

        tableView = new TableView<>();

        TableColumn<ItemData, String> dateCol = new TableColumn<>("등록일");
        dateCol.setCellValueFactory(cellData -> cellData.getValue().dateProperty());

        TableColumn<ItemData, String> titleCol = new TableColumn<>("상품명");
        titleCol.setCellValueFactory(cellData -> cellData.getValue().titleProperty());

        TableColumn<ItemData, String> priceCol = new TableColumn<>("가격");
        priceCol.setCellValueFactory(cellData -> cellData.getValue().priceProperty());

        TableColumn<ItemData, String> regionCol = new TableColumn<>("지역");
        regionCol.setCellValueFactory(cellData -> cellData.getValue().regionProperty());

        TableColumn<ItemData, Hyperlink> linkCol = new TableColumn<>("링크");
        linkCol.setCellValueFactory(cellData -> cellData.getValue().linkProperty());

        tableView.getColumns().addAll(dateCol, titleCol, priceCol, regionCol, linkCol);

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(15));
        layout.getChildren().addAll(searchField, regionComboBox, searchButton, urlBox, tableView);
        layout.setStyle("-fx-background-color: #FFF5EC;");

        Scene scene = new Scene(layout, 1000, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void searchItems() {
        String keyword = searchField.getText().trim();
        String selectedRegion = regionComboBox.getValue();

        if (keyword.isEmpty() || selectedRegion == null || selectedRegion.isEmpty()) {
            System.out.println("[WARN] 검색어 또는 지역이 비어있습니다.");
            return;
        }

        tableView.getItems().clear();
        resultUrls.clear();

        String baseUrl;
        boolean isProvince = Arrays.asList(provinces).contains(selectedRegion);

        if (isProvince) {
            baseUrl = "https://www.daangn.com/kr/buy-sell/?search=" +
                    URLEncoder.encode(selectedRegion + " " + keyword, StandardCharsets.UTF_8);
        } else {
            String areaCode = null;
            for (String[] region : regions) {
                if (region[0].equals(selectedRegion)) {
                    areaCode = region[1];
                    break;
                }
            }
            if (areaCode == null) {
                System.out.println("[ERROR] 지역 코드 매칭 실패");
                return;
            }
            baseUrl = "https://www.daangn.com/kr/buy-sell/?in=" + areaCode +
                    "&search=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        }

        String saleUrl = baseUrl + "&only_on_sale=true";
        String allUrl = baseUrl;

        javafx.application.Platform.runLater(() -> {
            saleUrlLink.setText(saleUrl);
            allUrlLink.setText(allUrl);
        });

        new Thread(() -> {
            try {
                Document doc = Jsoup.connect(saleUrl).get();
                Elements items = doc.select(".flea-market-article-link");

                for (Element item : items) {
                    String title = item.select(".article-title").text();
                    String price = item.select(".article-price").text();
                    String regionName = item.select(".article-region-name").text();
                    String date = item.select(".article-timeago").text();
                    String itemUrl = "https://www.daangn.com" + item.attr("href");

                    Hyperlink link = new Hyperlink("열기");
                    link.setOnAction(e -> openWebpage(itemUrl));

                    ItemData data = new ItemData(date, title, price, regionName, link);

                    javafx.application.Platform.runLater(() -> {
                        tableView.getItems().add(data);
                        resultUrls.add(itemUrl);
                    });
                }

                System.out.println("[INFO] 크롤링 완료. 결과 수: " + items.size());

            } catch (IOException e) {
                System.out.println("[ERROR] 크롤링 중 오류 발생");
                e.printStackTrace();
            }
        }).start();
    }

    private void openWebpage(String url) {
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (IOException e) {
            System.out.println("[ERROR] 브라우저 열기 실패");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

class ItemData {
    private final SimpleStringProperty date;
    private final SimpleStringProperty title;
    private final SimpleStringProperty price;
    private final SimpleStringProperty region;
    private final SimpleObjectProperty<Hyperlink> link;

    public ItemData(String date, String title, String price, String region, Hyperlink link) {
        this.date = new SimpleStringProperty(date);
        this.title = new SimpleStringProperty(title);
        this.price = new SimpleStringProperty(price);
        this.region = new SimpleStringProperty(region);
        this.link = new SimpleObjectProperty<>(link);
    }

    public SimpleStringProperty dateProperty() { return date; }
    public SimpleStringProperty titleProperty() { return title; }
    public SimpleStringProperty priceProperty() { return price; }
    public SimpleStringProperty regionProperty() { return region; }
    public SimpleObjectProperty<Hyperlink> linkProperty() { return link; }
}