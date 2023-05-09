import {Lines} from "@common/models/TreeGraph/Line";

export class Arrow {
    lines: Lines;

    constructor() {
        this.lines = new Lines();
    }
}

export class Arrows {
    all: Arrow[];

    constructor() {
        this.all = [];
    }
}