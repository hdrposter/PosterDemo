package com.facedetector.customview;

public class GridItem {
    private int id;
    private String name;

    public GridItem(){}
    public GridItem(int id,String name){
        this.id = id;
        this.name = name;
    }

    public  int getId(){
        return id;
    }

    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name = name;
    }

    public void setId(int id){
        this.id = id;
    }
}
