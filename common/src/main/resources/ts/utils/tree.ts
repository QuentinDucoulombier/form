export class TreeUtils {
    static shuffle = (a) : any =>  {
        let j, x, i;
        for (i = a.length; i; i -= 1) {
            j = Math.floor(Math.random() * i);
            x = a[i - 1];
            a[i - 1] = a[j];
            a[j] = x;
        }
        return a;
    }

    static intersects = (x1: number, y1: number, x2: number, y2: number, x3: number, y3: number, x4: number, y4: number) : boolean => {
        let det: number = (x2 - x1) * (y4 - y3) - (x4 - x3) * (y2 - y1);
        if (det === 0) return false;

        let lambda = ((y4 - y3) * (x4 - x1) + (x3 - x4) * (y4 - y1)) / det;
        let gamma = ((y1 - y2) * (x4 - x1) + (x2 - x1) * (y4 - y1)) / det;
        return (0 < lambda && lambda < 1) && (0 < gamma && gamma < 1);
    }
}