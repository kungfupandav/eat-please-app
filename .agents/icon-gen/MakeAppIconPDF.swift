import AppKit
import Foundation

let root = "/Users/vik/repos/eat-please-app"
let stickerPath = "\(root)/assets/icons/eat-pls-icon.png"
let outPDF = "\(root)/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.pdf"
let outAssetsPDF = "\(root)/assets/icons/eat-pls-icon.pdf"
let outPNG = "\(root)/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png"
let previewPNG = "\(root)/.agents/icon-gen/ios_pdf_raster_preview.png"
let fracPath = "\(root)/.agents/icon-gen/ios_sticker_frac.txt"

guard let stickerNS = NSImage(contentsOfFile: stickerPath),
      let stickerCG = stickerNS.cgImage(forProposedRect: nil, context: nil, hints: nil) else {
    fputs("Failed to load sticker\n", stderr); exit(1)
}

let pageSize = CGSize(width: 1024, height: 1024)
let yellow = NSColor(srgbRed: 253/255.0, green: 230/255.0, blue: 11/255.0, alpha: 1)
let stickerFrac: CGFloat = {
    if let s = try? String(contentsOfFile: fracPath, encoding: .utf8),
       let v = Double(s.trimmingCharacters(in: .whitespacesAndNewlines)) {
        return CGFloat(v)
    }
    return 0.78
}()

func transparentSticker(from cg: CGImage) -> CGImage {
    let w = cg.width, h = cg.height
    let colorSpace = CGColorSpaceCreateDeviceRGB()
    let bytesPerRow = w * 4
    var data = Data(count: bytesPerRow * h)
    data.withUnsafeMutableBytes { raw in
        guard let base = raw.baseAddress,
              let ctx = CGContext(data: base, width: w, height: h, bitsPerComponent: 8,
                                  bytesPerRow: bytesPerRow, space: colorSpace,
                                  bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        let ptr = base.assumingMemoryBound(to: UInt8.self)
        for i in 0..<(w*h) {
            let o = i * 4
            let r = Int(ptr[o]), g = Int(ptr[o+1]), b = Int(ptr[o+2])
            if r > 240 && g > 240 && b > 240 {
                ptr[o]=0; ptr[o+1]=0; ptr[o+2]=0; ptr[o+3]=0
            } else if (r+g+b)/3 > 230 && !(r > 200 && g > 180 && b < 120) {
                ptr[o+3] = 0
            }
        }
    }
    return data.withUnsafeMutableBytes { raw -> CGImage in
        let ctx = CGContext(data: raw.baseAddress, width: w, height: h, bitsPerComponent: 8,
                            bytesPerRow: bytesPerRow, space: colorSpace,
                            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        return ctx.makeImage()!
    }
}

let sticker = transparentSticker(from: stickerCG)

func drawIcon(in ctx: CGContext) {
    ctx.setFillColor(yellow.cgColor)
    ctx.fill(CGRect(origin: .zero, size: pageSize))
    let target = pageSize.width * stickerFrac
    let scale = target / CGFloat(max(sticker.width, sticker.height))
    let nw = CGFloat(sticker.width) * scale
    let nh = CGFloat(sticker.height) * scale
    let dx = (pageSize.width - nw) / 2
    let dy = (pageSize.height - nh) / 2
    ctx.draw(sticker, in: CGRect(x: dx, y: dy, width: nw, height: nh))
}

func writePDF(to path: String) {
    var mediaBox = CGRect(origin: .zero, size: pageSize)
    guard let ctx = CGContext(URL(fileURLWithPath: path) as CFURL, mediaBox: &mediaBox, nil) else {
        fputs("PDF failed \(path)\n", stderr); exit(1)
    }
    ctx.beginPDFPage(nil)
    drawIcon(in: ctx)
    ctx.endPDFPage()
    ctx.closePDF()
    print("Wrote PDF \(path) frac=\(stickerFrac)")
}

writePDF(to: outPDF)
writePDF(to: outAssetsPDF)

let colorSpace = CGColorSpaceCreateDeviceRGB()
let bmp = CGContext(data: nil, width: Int(pageSize.width), height: Int(pageSize.height),
                    bitsPerComponent: 8, bytesPerRow: 0, space: colorSpace,
                    bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
drawIcon(in: bmp)
let cgOut = bmp.makeImage()!
let png = NSBitmapImageRep(cgImage: cgOut).representation(using: .png, properties: [:])!
try png.write(to: URL(fileURLWithPath: outPNG))
try png.write(to: URL(fileURLWithPath: previewPNG))
print("Wrote PNG companion")
