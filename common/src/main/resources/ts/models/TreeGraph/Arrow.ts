import {Lines} from "@common/models/TreeGraph/Line";

export class Arrow {
    lines: Lines;

    constructor() {
        this.lines = new Lines();
    }

    arrowsIntersect = (arrow: Arrow) : boolean => {
        for (let lineA of this.lines.all) {
            for (let lineB of arrow.lines.all) {
                let intersecting: boolean = lineA.linesIntersect(lineB);
                if (intersecting) return true;
            }
        }
        return false;
    }
}

export class Arrows {
    all: Arrow[];

    constructor() {
        this.all = [];
    }

    countNbCollisions = () : number => {
        let collisionCnt: number = 0;
        for (let arrow of this.all) {
            collisionCnt += this.all.filter((a: Arrow) => a != arrow && arrow.arrowsIntersect(a)).length;
        }
        return collisionCnt;
    }
}